package online.yudream.base.domain.platform.mobile.repo;

import online.yudream.base.domain.platform.mobile.aggregate.MobileDevice;

import java.util.List;
import java.util.Optional;

public interface MobileDeviceRepo {

    MobileDevice save(MobileDevice device);

    Optional<MobileDevice> findById(Long id);

    /** 幂等 upsert 的查找键：用户 + 推送 token，逻辑删除记录不算命中。 */
    Optional<MobileDevice> findByUserIdAndPushToken(Long userId, String pushToken);

    /** 当前用户的未注销设备列表，按注册时间倒序。 */
    List<MobileDevice> findByUserId(Long userId);

    /** 删除该用户的全部设备注册（账号删除级联，停止向其推送）。 */
    void deleteByUserId(Long userId);
}
