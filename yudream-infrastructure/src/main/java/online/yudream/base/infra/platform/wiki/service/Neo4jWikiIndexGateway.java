package online.yudream.base.infra.platform.wiki.service;

import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.graph.aggregate.GraphConnection;
import online.yudream.base.domain.platform.graph.repo.GraphConnectionRepo;
import online.yudream.base.domain.platform.wiki.aggregate.WikiPageVersion;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSpace;
import online.yudream.base.domain.platform.wiki.service.WikiEmbeddingGateway;
import online.yudream.base.domain.platform.wiki.service.WikiGraphExtractionGateway;
import online.yudream.base.domain.platform.wiki.service.WikiIndexGateway;
import online.yudream.base.domain.platform.wiki.valobj.*;
import online.yudream.base.infra.platform.graph.service.Neo4jGraphDatabaseGateway;
import org.neo4j.driver.Session;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.function.Consumer;

/** Wiki index implementation sharing the sole deployment Neo4j Driver and table-scoped fixed Cypher. */
@Service
public class Neo4jWikiIndexGateway implements WikiIndexGateway {
    private final WikiEmbeddingGateway embeddingGateway;
    private final WikiGraphExtractionGateway graphExtractionGateway;
    private final GraphConnectionRepo graphTables;
    private final Neo4jGraphDatabaseGateway graphGateway;
    public Neo4jWikiIndexGateway(WikiEmbeddingGateway embeddingGateway, WikiGraphExtractionGateway graphExtractionGateway,
                                 GraphConnectionRepo graphTables, Neo4jGraphDatabaseGateway graphGateway) {
        this.embeddingGateway=embeddingGateway; this.graphExtractionGateway=graphExtractionGateway;
        this.graphTables=graphTables; this.graphGateway=graphGateway;
    }
    @Override public void index(WikiSpace space, WikiPageVersion version, String path, Consumer<WikiIndexProgress> progress) {
        GraphConnection table=table(space); String tableCode=table.getCode();
        List<String> chunks=chunks(version.getMarkdown(),space.getChunkSize(),space.getChunkOverlap());
        progress.accept(new WikiIndexProgress("chunking","已完成 Markdown 分块，共 "+chunks.size()+" 段",20));
        if(space.getEmbeddingProviderCode().isBlank()||space.getEmbeddingModelCode().isBlank()) throw new IllegalArgumentException("知识库未配置 Embedding Provider 和模型");
        List<List<Float>> embeddings=new ArrayList<>(chunks.size());
        for(int i=0;i<chunks.size();i++) embeddings.add(embeddingGateway.embed(space.getEmbeddingProviderCode(),space.getEmbeddingModelCode(),List.of(chunks.get(i))).getFirst());
        List<WikiGraphRelation> relations=space.isGraphEnabled()?graphExtractionGateway.extract(space.getGraphProviderCode(),space.getGraphModelCode(),version.getTitle(),version.getMarkdown()):List.of();
        int dimensions=embeddings.getFirst().size();String label=vectorLabel(dimensions);String index=vectorIndex(dimensions);
        progress.accept(new WikiIndexProgress("neo4j","正在写入 Neo4j 向量索引和图谱",85));
        try(Session session=graphGateway.openSession()) {
            session.executeWriteWithoutResult(tx->tx.run("CREATE VECTOR INDEX "+index+" IF NOT EXISTS FOR (c:"+label+") ON (c.embedding) OPTIONS {indexConfig: {`vector.dimensions`: $dimensions, `vector.similarity_function`: 'cosine'}}",Map.of("dimensions",dimensions)).consume());
            session.executeWriteWithoutResult(tx->{
                Map<String,Object> node=scope(tableCode,space.getId(),version.getNodeId(),version.getId());
                tx.run("MATCH (c:WikiChunk {tableCode:$tableCode,spaceId:$spaceId,nodeId:$nodeId}) SET c.active=false",node);
                tx.run("MATCH (c:WikiChunk {tableCode:$tableCode,spaceId:$spaceId,nodeId:$nodeId,versionId:$versionId}) DETACH DELETE c",node);
                for(int i=0;i<chunks.size();i++){Map<String,Object> p=new LinkedHashMap<>(node);p.put("sequence",i);p.put("title",version.getTitle());p.put("path",path);p.put("content",chunks.get(i));p.put("embedding",embeddings.get(i));tx.run("CREATE (c:WikiChunk:"+label+" {tableCode:$tableCode,spaceId:$spaceId,nodeId:$nodeId,versionId:$versionId,sequence:$sequence,title:$title,path:$path,content:$content,embedding:$embedding,active:true})",p);}
                tx.run("MATCH ()-[r:WIKI_RELATES {tableCode:$tableCode,spaceId:$spaceId,nodeId:$nodeId}]->() DELETE r",node);
                for(WikiGraphRelation relation:relations){Map<String,Object> p=new LinkedHashMap<>(node);p.put("source",relation.source().trim());p.put("sourceType",type(relation.sourceType()));p.put("target",relation.target().trim());p.put("targetType",type(relation.targetType()));p.put("relation",relation.relation().trim());p.put("confidence",relation.confidence());tx.run("MERGE (source:WikiEntity {tableCode:$tableCode,spaceId:$spaceId,key:toLower($source),type:$sourceType}) MERGE (target:WikiEntity {tableCode:$tableCode,spaceId:$spaceId,key:toLower($target),type:$targetType}) MERGE (source)-[r:WIKI_RELATES {tableCode:$tableCode,spaceId:$spaceId,nodeId:$nodeId,versionId:$versionId,relation:$relation}]->(target) SET r.confidence=$confidence",p);}
            });
        }
        progress.accept(new WikiIndexProgress("neo4j","Neo4j 写入完成",95));
    }
    @Override public void remove(WikiSpace space,Long nodeId,Long versionId){String code=table(space).getCode();try(Session s=graphGateway.openSession()){Map<String,Object>p=scope(code,space.getId(),nodeId,versionId);s.run("MATCH (c:WikiChunk {tableCode:$tableCode,spaceId:$spaceId,nodeId:$nodeId,versionId:$versionId}) DETACH DELETE c",p);s.run("MATCH ()-[r:WIKI_RELATES {tableCode:$tableCode,spaceId:$spaceId,nodeId:$nodeId,versionId:$versionId}]->() DELETE r",p);}}
    @Override public WikiIndexSnapshot inspect(WikiSpace space,Long nodeId,Long versionId){String code=table(space).getCode();Map<String,Object>p=scope(code,space.getId(),nodeId,versionId);try(Session s=graphGateway.openSession()){var cr=s.run("MATCH (c:WikiChunk {tableCode:$tableCode,spaceId:$spaceId,nodeId:$nodeId,versionId:$versionId}) RETURN c ORDER BY c.sequence",p);List<WikiChunk>chunks=new ArrayList<>();while(cr.hasNext()){var c=cr.next().get("c").asNode();chunks.add(new WikiChunk(space.getId(),nodeId,versionId,c.get("sequence").asInt(),c.get("title").asString(),c.get("path").asString(),c.get("content").asString()));}var rr=s.run("MATCH (source:WikiEntity {tableCode:$tableCode,spaceId:$spaceId})-[r:WIKI_RELATES {tableCode:$tableCode,spaceId:$spaceId,nodeId:$nodeId,versionId:$versionId}]->(target:WikiEntity {tableCode:$tableCode,spaceId:$spaceId}) RETURN source,r,target ORDER BY r.confidence DESC",p);List<WikiGraphRelation>relations=new ArrayList<>();while(rr.hasNext()){var row=rr.next();var a=row.get("source").asNode();var b=row.get("target").asNode();var r=row.get("r").asRelationship();relations.add(new WikiGraphRelation(a.get("key").asString(),a.get("type").asString(),r.get("relation").asString(),b.get("key").asString(),b.get("type").asString(),r.get("confidence").asDouble()));}return new WikiIndexSnapshot(chunks,relations);}}
    @Override public List<WikiSearchHit> search(WikiSpace space,String query,int topK,String pathPrefix,boolean graphExpansion){String normalized=query==null?"":query.trim();if(normalized.isBlank())return List.of();if(space.getEmbeddingProviderCode().isBlank()||space.getEmbeddingModelCode().isBlank())throw new IllegalArgumentException("知识库未配置 Embedding 模型");String code=table(space).getCode();List<Float>vector=embeddingGateway.embed(space.getEmbeddingProviderCode(),space.getEmbeddingModelCode(),List.of(normalized)).getFirst();try(Session s=graphGateway.openSession()){int limit=Math.clamp(topK,1,30);Map<String,Object>p=new LinkedHashMap<>();p.put("index",vectorIndex(vector.size()));p.put("candidateLimit",Math.clamp(topK*4,10,100));p.put("vector",vector);p.put("tableCode",code);p.put("spaceId",space.getId());p.put("path",pathPrefix==null?"":pathPrefix);p.put("limit",limit);var rows=s.run("CALL db.index.vector.queryNodes($index,$candidateLimit,$vector) YIELD node,score WHERE node.tableCode=$tableCode AND node.spaceId=$spaceId AND node.active=true AND ($path='' OR node.path STARTS WITH $path) RETURN node,score ORDER BY score DESC LIMIT $limit",p);List<WikiSearchHit>hits=new ArrayList<>();while(rows.hasNext()){var r=rows.next();var c=r.get("node").asNode();hits.add(new WikiSearchHit(r.get("score").asDouble(),c.get("nodeId").asLong(),c.get("title").asString(),c.get("path").asString(),c.get("content").asString()));}return hits;}catch(org.neo4j.driver.exceptions.ClientException e){if(e.getMessage()!=null&&e.getMessage().contains("no such vector schema index"))throw new BizException("知识库尚未建立向量索引，请先发布或重建索引");throw e;}}
    private GraphConnection table(WikiSpace space){if(space==null||space.getGraphTableCode()==null||space.getGraphTableCode().isBlank())throw new BizException("知识库必须选择逻辑图表");GraphConnection table=graphTables.findByCode(space.getGraphTableCode()).orElseThrow(()->new BizException("知识库选择的逻辑图表不存在"));if(!table.active())throw new BizException("知识库选择的逻辑图表已停用");return table;}
    private static Map<String,Object> scope(String tableCode,Long spaceId,Long nodeId,Long versionId){return Map.of("tableCode",tableCode,"spaceId",spaceId,"nodeId",nodeId,"versionId",versionId);}
    private static String vectorLabel(int dimensions){return "WikiChunkVector"+dimensions;}private static String vectorIndex(int dimensions){return "wiki_chunk_vector_"+dimensions;}private static String type(String v){return v==null||v.isBlank()?"CONCEPT":v.trim().toUpperCase().replaceAll("[^A-Z0-9_]","_");}private static List<String> chunks(String markdown,int size,int overlap){String v=markdown==null?"":markdown.trim();if(v.isEmpty())return List.of("");List<String>out=new ArrayList<>();for(int from=0;;){int end=Math.min(v.length(),from+size);out.add(v.substring(from,end));if(end==v.length())return out;from=Math.max(from+1,end-overlap);}}
}
