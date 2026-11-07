package com.nsu.capstone.event;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

/**
 * record의 컴포넌트를 이름으로 읽고 하나만 바꿔 다시 만드는 테스트 도우미. 행사 필드를 더할 때 빠뜨린 곳을
 * 찾는 안전망 테스트(EventFieldCoverageTest, FingerprintSnapshotAgreementTest)가 쓴다.
 */
public final class RecordReflection {

    private RecordReflection() {
    }

    public static List<String> names(Class<? extends Record> type) {
        return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList();
    }

    public static Object value(Record record, String component) throws Exception {
        for (RecordComponent candidate : record.getClass().getRecordComponents()) {
            if (candidate.getName().equals(component)) {
                return candidate.getAccessor().invoke(record);
            }
        }
        throw new AssertionError(record.getClass().getSimpleName() + "에 " + component + "가 없다");
    }

    /** base에서 component 하나만 value로 바꾼 record를 정식 생성자로 만든다. 불변식 위반이면 픽스처가 잘못된 것이다. */
    public static <R extends Record> R rebuildWith(Class<R> type, R base, String component, Object value)
        throws Exception {
        RecordComponent[] parts = type.getRecordComponents();
        Class<?>[] types = new Class<?>[parts.length];
        Object[] args = new Object[parts.length];
        for (int i = 0; i < parts.length; i++) {
            types[i] = parts[i].getType();
            args[i] = parts[i].getName().equals(component) ? value : parts[i].getAccessor().invoke(base);
        }
        try {
            return type.getDeclaredConstructor(types).newInstance(args);
        } catch (InvocationTargetException failure) {
            throw new AssertionError(type.getSimpleName() + "." + component
                + ": alternate()의 값이 다른 컴포넌트와 독립적으로 유효하지 않다", failure.getCause());
        }
    }
}
