package io.healthresetplan.modules.content;

import java.util.Set;

final class ContentSafetyGuard {

    private static final Set<String> FORBIDDEN = Set.of(
            "诊断为", "确诊为", "治疗方案", "治疗建议", "建议服用", "推荐服用",
            "药物剂量", "用药剂量", "自行停药", "调整用药", "替代药物",
            "疗效显著", "保证治愈", "可以治愈", "处方建议", "医疗建议");

    private ContentSafetyGuard() {
    }

    static boolean isSafe(String text) {
        String value = text == null ? "" : text.replaceAll("\\s+", "");
        return FORBIDDEN.stream().noneMatch(value::contains);
    }
}
