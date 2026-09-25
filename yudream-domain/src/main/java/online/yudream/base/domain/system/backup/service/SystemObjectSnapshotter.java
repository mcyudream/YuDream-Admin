package online.yudream.base.domain.system.backup.service;

import online.yudream.base.domain.system.file.valobj.StoredObject;

import java.util.function.Consumer;

/**
 * 系统对象存储快照端口：流式遍历对象存储中全部对象（含插件资产与用户上传文件）。
 * consumer 处理完必须关闭 {@link StoredObject#inputStream()}。
 */
public interface SystemObjectSnapshotter {

    void streamObjects(Consumer<StoredObject> consumer);
}
