package online.yudream.base.interfaces.platform.feishu.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.feishu.service.FeishuAppService;
import online.yudream.base.domain.system.security.anno.PermissionRegister;
import online.yudream.base.interfaces.common.Result;
import online.yudream.base.interfaces.platform.feishu.assembler.FeishuWebAssembler;
import online.yudream.base.interfaces.platform.feishu.request.FeishuBlockAppendRequest;
import online.yudream.base.interfaces.platform.feishu.request.FeishuBlockDeleteRequest;
import online.yudream.base.interfaces.platform.feishu.request.FeishuElementUpdateRequest;
import online.yudream.base.interfaces.platform.feishu.res.FeishuBlockWriteResultRes;
import online.yudream.base.interfaces.platform.feishu.res.FeishuCloudDocPageRes;
import online.yudream.base.interfaces.platform.feishu.res.FeishuDocumentBlockPageRes;
import online.yudream.base.interfaces.platform.feishu.res.FeishuDocumentContentRes;
import online.yudream.base.interfaces.platform.feishu.res.FeishuDocumentMetaRes;
import online.yudream.base.interfaces.platform.feishu.res.FeishuWikiNodePageRes;
import online.yudream.base.interfaces.platform.feishu.res.FeishuWikiSpacePageRes;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 飞书云文档接口（本工作项唯一在范围的 API 模块）。
 * <p>
 * 项目闸门 {@code yudream.platform.capabilities.feishu.enabled} 关闭时不注册端点；应用闸门在应用层逐用例检查。
 * 读取返回飞书原始 JSON（块 raw 保真）；写入只有飞书官方支持的形态：追加子块、删除子块、更新文本块 elements，
 * 不提供整块 PATCH 假编辑。所有调用按用户分钟/日额度计数，前端不传归属用户。
 */
@RestController
@RequestMapping("/api/platform/feishu")
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.feishu", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class FeishuCloudDocController {

    private final FeishuAppService feishuAppService;

    @GetMapping("/drive/files")
    @PermissionRegister(code = "platform:feishu:view", name = "查看飞书云文档", module = "平台能力", desc = "列出飞书云空间文件与文件夹")
    public Result<FeishuCloudDocPageRes> driveFiles(@RequestParam(required = false) String folderToken,
                                                    @RequestParam(required = false) String pageToken,
                                                    @RequestParam(required = false) Integer pageSize) {
        return Result.ok(FeishuWebAssembler.toRes(feishuAppService.driveFiles(
                FeishuWebAssembler.driveFileQuery(folderToken, pageToken, pageSize))));
    }

    @GetMapping("/wiki/spaces")
    @PermissionRegister(code = "platform:feishu:view", name = "查看飞书知识库", module = "平台能力", desc = "列出飞书知识库空间")
    public Result<FeishuWikiSpacePageRes> wikiSpaces(@RequestParam(required = false) String pageToken,
                                                     @RequestParam(required = false) Integer pageSize) {
        return Result.ok(FeishuWebAssembler.toRes(feishuAppService.wikiSpaces(
                FeishuWebAssembler.wikiSpaceQuery(pageToken, pageSize))));
    }

    @GetMapping("/wiki/spaces/{spaceId}/nodes")
    @PermissionRegister(code = "platform:feishu:view", name = "查看飞书知识库节点", module = "平台能力", desc = "分页列出知识空间节点，parentNodeToken 为空表示根级")
    public Result<FeishuWikiNodePageRes> wikiNodes(@PathVariable String spaceId,
                                                   @RequestParam(required = false) String parentNodeToken,
                                                   @RequestParam(required = false) String pageToken,
                                                   @RequestParam(required = false) Integer pageSize) {
        return Result.ok(FeishuWebAssembler.toRes(feishuAppService.wikiNodes(
                FeishuWebAssembler.wikiNodeQuery(spaceId, parentNodeToken, pageToken, pageSize))));
    }

    @GetMapping("/documents/{documentId}")
    @PermissionRegister(code = "platform:feishu:view", name = "查看飞书文档元信息", module = "平台能力", desc = "读取新版文档标题与版本号")
    public Result<FeishuDocumentMetaRes> documentMeta(@PathVariable String documentId) {
        return Result.ok(FeishuWebAssembler.toRes(feishuAppService.documentMeta(
                FeishuWebAssembler.documentQuery(documentId, null))));
    }

    @GetMapping("/documents/{documentId}/content")
    @PermissionRegister(code = "platform:feishu:view", name = "查看飞书文档内容", module = "平台能力", desc = "读取新版文档纯文本内容（raw_content 原样返回）")
    public Result<FeishuDocumentContentRes> documentContent(@PathVariable String documentId,
                                                            @RequestParam(required = false) Integer lang) {
        return Result.ok(FeishuWebAssembler.toRes(feishuAppService.documentContent(
                FeishuWebAssembler.documentQuery(documentId, lang))));
    }

    @GetMapping("/documents/{documentId}/blocks")
    @PermissionRegister(code = "platform:feishu:view", name = "查看飞书文档块", module = "平台能力", desc = "分页读取新版文档块，raw 为飞书原始块 JSON")
    public Result<FeishuDocumentBlockPageRes> documentBlocks(@PathVariable String documentId,
                                                             @RequestParam(required = false) String pageToken,
                                                             @RequestParam(required = false) Integer pageSize) {
        return Result.ok(FeishuWebAssembler.toRes(feishuAppService.documentBlocks(
                FeishuWebAssembler.blockQuery(documentId, pageToken, pageSize))));
    }

    @PostMapping("/documents/{documentId}/blocks/{parentBlockId}/children")
    @PermissionRegister(code = "platform:feishu:edit", name = "编辑飞书文档子块", module = "平台能力", desc = "在父块下追加子块，children 为飞书块 JSON 原文（保真）")
    public Result<FeishuBlockWriteResultRes> appendChildren(@PathVariable String documentId,
                                                            @PathVariable String parentBlockId,
                                                            @Valid @RequestBody FeishuBlockAppendRequest request) {
        return Result.ok(FeishuWebAssembler.toRes(feishuAppService.appendBlocks(
                FeishuWebAssembler.appendCmd(documentId, parentBlockId, request))));
    }

    @DeleteMapping("/documents/{documentId}/blocks/{parentBlockId}/children")
    @PermissionRegister(code = "platform:feishu:edit", name = "删除飞书文档子块", module = "平台能力", desc = "删除父块下指定下标区间的子块")
    public Result<Void> deleteChildren(@PathVariable String documentId,
                                       @PathVariable String parentBlockId,
                                       @Valid @RequestBody FeishuBlockDeleteRequest request) {
        feishuAppService.deleteBlocks(FeishuWebAssembler.deleteCmd(documentId, parentBlockId, request));
        return Result.ok();
    }

    @PatchMapping("/documents/{documentId}/blocks/{blockId}/elements")
    @PermissionRegister(code = "platform:feishu:edit", name = "编辑飞书文档文本块", module = "平台能力", desc = "按 update_text_elements 更新文本承载块的元素，elements 为元素 JSON 原文")
    public Result<FeishuBlockWriteResultRes> updateElements(@PathVariable String documentId,
                                                            @PathVariable String blockId,
                                                            @Valid @RequestBody FeishuElementUpdateRequest request) {
        return Result.ok(FeishuWebAssembler.toRes(feishuAppService.updateBlockElements(
                FeishuWebAssembler.elementUpdateCmd(documentId, blockId, request))));
    }
}
