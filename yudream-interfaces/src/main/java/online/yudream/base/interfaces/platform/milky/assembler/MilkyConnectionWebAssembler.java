package online.yudream.base.interfaces.platform.milky.assembler;

import online.yudream.base.application.platform.milky.cmd.MilkyConnectionCreateCmd;
import online.yudream.base.application.platform.milky.cmd.MilkyConnectionUpdateCmd;
import online.yudream.base.application.platform.milky.dto.MilkyConnectionDTO;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.interfaces.platform.milky.request.MilkyConnectionCreateRequest;
import online.yudream.base.interfaces.platform.milky.request.MilkyConnectionUpdateRequest;
import online.yudream.base.interfaces.platform.milky.res.MilkyConnectionRes;
public final class MilkyConnectionWebAssembler {
 private MilkyConnectionWebAssembler() { }
 public static MilkyConnectionCreateCmd toCmd(MilkyConnectionCreateRequest r) { MilkyConnectionCreateCmd c=new MilkyConnectionCreateCmd(); c.setName(r.getName()); c.setBaseUrl(r.getBaseUrl()); c.setToken(r.getToken()); c.setCommandMenuImageMode(r.getCommandMenuImageMode()); c.setCommandMenuPublicBaseUrl(r.getCommandMenuPublicBaseUrl()); return c; }
 public static MilkyConnectionUpdateCmd toCmd(Long id, MilkyConnectionUpdateRequest r) { MilkyConnectionUpdateCmd c=new MilkyConnectionUpdateCmd(); c.setId(id); c.setName(r.getName()); c.setBaseUrl(r.getBaseUrl()); c.setToken(r.getToken()); c.setCommandMenuImageMode(r.getCommandMenuImageMode()); c.setCommandMenuPublicBaseUrl(r.getCommandMenuPublicBaseUrl()); return c; }
 public static MilkyConnectionRes toRes(MilkyConnectionDTO d) { return MilkyConnectionRes.builder().id(d.getId()==null?null:String.valueOf(d.getId())).name(d.getName()).baseUrl(d.getBaseUrl()).enabled(d.isEnabled()).credentialConfigured(d.isCredentialConfigured()).commandMenuImageMode(d.getCommandMenuImageMode()).commandMenuPublicBaseUrl(d.getCommandMenuPublicBaseUrl()).createTime(d.getCreateTime()).updateTime(d.getUpdateTime()).build(); }
 public static PageResult<MilkyConnectionRes> toRes(PageResult<MilkyConnectionDTO> page) { return new PageResult<>(page.getRecords().stream().map(MilkyConnectionWebAssembler::toRes).toList(),page.getTotal(),page.getPage(),page.getSize()); }
}
