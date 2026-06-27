package online.yudream.base.infra.persistence.mongodb;

import online.yudream.base.domain.shared.IdGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.mapping.event.AbstractMongoEventListener;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertEvent;
import org.springframework.stereotype.Component;

@Component
public class SnowflakeIdInjectListener extends AbstractMongoEventListener<Object> {

    @Autowired
    private IdGenerator idGenerator;

    @Override
    public void onBeforeConvert(BeforeConvertEvent<Object> event) {
        Object source = event.getSource();

        // 反射注入 id 字段（如果为空）
        try {
            java.lang.reflect.Field idField = findIdField(source.getClass());
            if (idField != null) {
                idField.setAccessible(true);
                Object currentId = idField.get(source);

                // 仅当 id 为空时注入
                if (currentId == null) {
                    Class<?> idType = idField.getType();
                    if (idType == Long.class || idType == long.class) {
                        idField.set(source, idGenerator.nextId());
                    }
                }
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException("ID 注入失败", e);
        }
    }

    /**
     * 递归查找 @Id 注解字段
     */
    private java.lang.reflect.Field findIdField(Class<?> clazz) {
        if (clazz == null || clazz == Object.class) {
            return null;
        }

        for (java.lang.reflect.Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(org.springframework.data.annotation.Id.class)) {
                return field;
            }
        }

        // 查找父类
        return findIdField(clazz.getSuperclass());
    }
}
