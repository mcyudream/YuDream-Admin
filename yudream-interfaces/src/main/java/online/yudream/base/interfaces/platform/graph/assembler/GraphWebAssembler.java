package online.yudream.base.interfaces.platform.graph.assembler;

import online.yudream.base.application.platform.graph.cmd.GraphConnectionSaveCmd;
import online.yudream.base.application.platform.graph.dto.GraphConnectionDTO;
import online.yudream.base.application.platform.graph.dto.GraphQueryLogDTO;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.interfaces.platform.graph.request.GraphConnectionSaveRequest;
import online.yudream.base.interfaces.platform.graph.res.GraphConnectionRes;
import online.yudream.base.interfaces.platform.graph.res.GraphQueryLogRes;

public class GraphWebAssembler {
    private GraphWebAssembler() { }
    public static GraphConnectionSaveCmd toCmd(GraphConnectionSaveRequest r){return toCmd(null,r);}
    public static GraphConnectionSaveCmd toCmd(Long id,GraphConnectionSaveRequest r){GraphConnectionSaveCmd c=new GraphConnectionSaveCmd();c.setId(id);c.setName(r.getName());c.setCode(r.getCode());c.setDescription(r.getDescription());c.setStatus(r.getStatus());c.setAuthorizedPluginCodes(r.getAuthorizedPluginCodes());return c;}
    public static PageResult<GraphConnectionRes> toConnectionPage(PageResult<GraphConnectionDTO> p){return new PageResult<>(p.getRecords().stream().map(GraphWebAssembler::toRes).toList(),p.getTotal(),p.getPage(),p.getSize());}
    public static PageResult<GraphQueryLogRes> toLogPage(PageResult<GraphQueryLogDTO> p){return new PageResult<>(p.getRecords().stream().map(GraphWebAssembler::toRes).toList(),p.getTotal(),p.getPage(),p.getSize());}
    public static GraphConnectionRes toRes(GraphConnectionDTO d){return GraphConnectionRes.builder().id(d.getId()).name(d.getName()).code(d.getCode()).description(d.getDescription()).status(d.getStatus()).authorizedPluginCodes(d.getAuthorizedPluginCodes()).createTime(d.getCreateTime()).updateTime(d.getUpdateTime()).build();}
    public static GraphQueryLogRes toRes(GraphQueryLogDTO d){return GraphQueryLogRes.builder().id(d.getId()).tableId(d.getTableId()).tableCode(d.getTableCode()).cypher(d.getCypher()).params(d.getParams()).rows(d.getRows()).summary(d.getSummary()).durationMillis(d.getDurationMillis()).status(d.getStatus()).errorMessage(d.getErrorMessage()).executedAt(d.getExecutedAt()).build();}
}
