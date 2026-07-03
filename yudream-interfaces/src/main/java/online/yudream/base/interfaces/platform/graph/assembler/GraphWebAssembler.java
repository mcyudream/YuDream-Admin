package online.yudream.base.interfaces.platform.graph.assembler;

import online.yudream.base.application.platform.graph.cmd.GraphConnectionSaveCmd;
import online.yudream.base.application.platform.graph.cmd.GraphQueryCmd;
import online.yudream.base.application.platform.graph.dto.GraphConnectionDTO;
import online.yudream.base.application.platform.graph.dto.GraphQueryLogDTO;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.interfaces.platform.graph.request.GraphConnectionSaveRequest;
import online.yudream.base.interfaces.platform.graph.request.GraphQueryRequest;
import online.yudream.base.interfaces.platform.graph.res.GraphConnectionRes;
import online.yudream.base.interfaces.platform.graph.res.GraphQueryLogRes;

public class GraphWebAssembler {

    private GraphWebAssembler() {
    }

    public static GraphConnectionSaveCmd toCmd(GraphConnectionSaveRequest request) {
        return toCmd(null, request);
    }

    public static GraphConnectionSaveCmd toCmd(Long id, GraphConnectionSaveRequest request) {
        GraphConnectionSaveCmd cmd = new GraphConnectionSaveCmd();
        cmd.setId(id);
        cmd.setName(request.getName());
        cmd.setCode(request.getCode());
        cmd.setUri(request.getUri());
        cmd.setUsername(request.getUsername());
        cmd.setPassword(request.getPassword());
        cmd.setDatabase(request.getDatabase());
        cmd.setStatus(request.getStatus());
        return cmd;
    }

    public static GraphQueryCmd toCmd(Long connectionId, GraphQueryRequest request) {
        GraphQueryCmd cmd = new GraphQueryCmd();
        cmd.setConnectionId(connectionId);
        cmd.setCypher(request.getCypher());
        cmd.setParams(request.getParams());
        return cmd;
    }

    public static PageResult<GraphConnectionRes> toConnectionPage(PageResult<GraphConnectionDTO> page) {
        return new PageResult<>(page.getRecords().stream().map(GraphWebAssembler::toRes).toList(), page.getTotal(), page.getPage(), page.getSize());
    }

    public static PageResult<GraphQueryLogRes> toLogPage(PageResult<GraphQueryLogDTO> page) {
        return new PageResult<>(page.getRecords().stream().map(GraphWebAssembler::toRes).toList(), page.getTotal(), page.getPage(), page.getSize());
    }

    public static GraphConnectionRes toRes(GraphConnectionDTO dto) {
        return GraphConnectionRes.builder()
                .id(dto.getId())
                .name(dto.getName())
                .code(dto.getCode())
                .uri(dto.getUri())
                .username(dto.getUsername())
                .database(dto.getDatabase())
                .status(dto.getStatus())
                .createTime(dto.getCreateTime())
                .updateTime(dto.getUpdateTime())
                .build();
    }

    public static GraphQueryLogRes toRes(GraphQueryLogDTO dto) {
        return GraphQueryLogRes.builder()
                .id(dto.getId())
                .connectionId(dto.getConnectionId())
                .connectionCode(dto.getConnectionCode())
                .cypher(dto.getCypher())
                .params(dto.getParams())
                .rows(dto.getRows())
                .summary(dto.getSummary())
                .durationMillis(dto.getDurationMillis())
                .status(dto.getStatus())
                .errorMessage(dto.getErrorMessage())
                .executedAt(dto.getExecutedAt())
                .build();
    }
}
