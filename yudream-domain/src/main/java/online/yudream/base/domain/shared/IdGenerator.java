package online.yudream.base.domain.shared;


/**
 * 分布式 ID 生成器接口 —— 定义在 Domain 层
 */
public interface IdGenerator {
    Long nextId();
}