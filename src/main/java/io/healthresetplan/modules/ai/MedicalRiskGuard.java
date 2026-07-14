package io.healthresetplan.modules.ai;

import java.util.Locale;
import java.util.Set;

public final class MedicalRiskGuard {

    private static final Set<String> URGENT_TERMS = Set.of(
            "胸痛", "呼吸困难", "呼吸困難", "昏迷", "晕厥", "暈厥", "中风", "中風",
            "自杀", "自殺", "大出血", "严重过敏", "嚴重過敏", "急救");
    private static final Set<String> CLINICAL_TERMS = Set.of(
            "怀孕", "懷孕", "孕期", "哺乳", "停药", "停藥", "加药", "加藥", "减药", "減藥",
            "药量", "藥量", "剂量", "劑量", "处方", "處方");

    private MedicalRiskGuard() {}

    public static String safetyReply(String text) {
        String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT);
        if (contains(normalized, URGENT_TERMS)) {
            return "你描述的情况可能需要紧急医疗评估。请立即联系当地急救服务或前往急诊；不要等待 AI 建议，也不要自行调整药物。";
        }
        if (contains(normalized, CLINICAL_TERMS)) {
            return "这涉及孕期、哺乳或用药调整等需要专业判断的事项。请联系医生或药师确认；我不能提供诊断、处方、剂量或停换药建议。";
        }
        return null;
    }

    private static boolean contains(String text, Set<String> terms) {
        return terms.stream().anyMatch(text::contains);
    }
}
