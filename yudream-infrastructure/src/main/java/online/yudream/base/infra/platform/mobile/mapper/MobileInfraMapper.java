package online.yudream.base.infra.platform.mobile.mapper;

import online.yudream.base.domain.platform.mobile.aggregate.MobileDevice;
import online.yudream.base.domain.platform.mobile.enumerate.MobilePlatform;
import online.yudream.base.infra.platform.mobile.dataobj.MobileDeviceDO;

public class MobileInfraMapper {

    private MobileInfraMapper() {
    }

    public static MobileDeviceDO toDataObj(MobileDevice device) {
        if (device == null) {
            return null;
        }
        MobileDeviceDO dataObj = new MobileDeviceDO();
        dataObj.setId(device.getId());
        dataObj.setUserId(device.getUserId());
        dataObj.setPlatform(device.getPlatform() == null ? null : device.getPlatform().name());
        dataObj.setPushChannel(device.getPushChannel());
        dataObj.setPushToken(device.getPushToken());
        dataObj.setDeviceName(device.getDeviceName());
        dataObj.setAppVersion(device.getAppVersion());
        dataObj.setHostVersion(device.getHostVersion());
        dataObj.setRegisteredAt(device.getRegisteredAt());
        dataObj.setLastSeenAt(device.getLastSeenAt());
        dataObj.setDeleted(device.getDeleted());
        dataObj.setVersion(device.getVersion());
        dataObj.setCreateTime(device.getCreateTime());
        dataObj.setUpdateTime(device.getUpdateTime());
        return dataObj;
    }

    public static MobileDevice toDomain(MobileDeviceDO dataObj) {
        if (dataObj == null) {
            return null;
        }
        return MobileDevice.builder()
                .id(dataObj.getId())
                .userId(dataObj.getUserId())
                .platform(parsePlatform(dataObj.getPlatform()))
                .pushChannel(dataObj.getPushChannel())
                .pushToken(dataObj.getPushToken())
                .deviceName(dataObj.getDeviceName())
                .appVersion(dataObj.getAppVersion())
                .hostVersion(dataObj.getHostVersion())
                .registeredAt(dataObj.getRegisteredAt())
                .lastSeenAt(dataObj.getLastSeenAt())
                .deleted(dataObj.getDeleted())
                .version(dataObj.getVersion())
                .createTime(dataObj.getCreateTime())
                .updateTime(dataObj.getUpdateTime())
                .build();
    }

    private static MobilePlatform parsePlatform(String platform) {
        if (platform == null || platform.isBlank()) {
            return null;
        }
        return MobilePlatform.valueOf(platform);
    }
}
