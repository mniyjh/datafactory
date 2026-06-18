package com.cqie.datafactory.executor.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * 从 JSON 执行结果中提取可读的格式化文本输出。
 * <p>
 * 解决问题：Python 脚本产生的自然语言报告（含 emoji、\n 换行、Unicode 框线）
 * 经过多层 JSON 序列化后，在 Web 前端显示为转义字符（\n、\t），
 * 编程小白无法直接阅读。
 * <p>
 * 本工具递归遍历输出 Map，找出所有"看起来像人类可读格式化文本"的字符串值，
 * 将它们提取并拼接为纯文本输出，前端可直接用 white-space: pre-wrap 渲染。
 *
 * @author DataFactory Team
 * @since 2026-06-18
 */
@Slf4j
public class OutputTextExtractor {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /** 一个叶子值至少这么长才被认为是"格式化文本" */
    private static final int MIN_TEXT_LENGTH = 30;

    /** 换行符超过这个数量才认为是格式化报告（而非普通含换行的简短 JSON） */
    private static final int MIN_LINE_COUNT = 2;

    /**
     * 从节点输出 Map 中提取人类可读的文本报告。
     *
     * @param outputs 节点/执行输出 Map
     * @return 提取的纯文本字符串，如果没有可提取的格式化文本则返回 null
     */
    public static String extract(Map<String, Object> outputs) {
        if (outputs == null || outputs.isEmpty()) return null;
        try {
            List<String> blocks = new ArrayList<>();
            collectTextBlocks(outputs, blocks, new HashSet<>());
            if (blocks.isEmpty()) return null;

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < blocks.size(); i++) {
                if (i > 0) sb.append("\n\n");
                sb.append(blocks.get(i));
            }
            return sb.toString();
        } catch (Exception e) {
            log.debug("OutputTextExtractor failed (non-fatal): {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从已序列化的 JSON 字符串中提取文本。
     * 适用于前端无法修改时，后端直接解析再提取。
     */
    public static String extractFromJson(String jsonStr) {
        if (jsonStr == null || jsonStr.isBlank()) return null;
        try {
            JsonNode node = objectMapper.readTree(jsonStr);
            Map<String, Object> map = objectMapper.convertValue(node, Map.class);
            return extract(map);
        } catch (Exception e) {
            log.debug("extractFromJson failed: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static void collectTextBlocks(Object value, List<String> blocks, Set<Integer> seenHash) {
        if (value == null) return;

        if (value instanceof String s) {
            if (isFormattedText(s)) {
                int h = s.hashCode();
                // 去重：相同的文本块只保留一次
                if (seenHash.add(h)) {
                    blocks.add(s);
                }
            }
            return;
        }

        if (value instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) value;
            // 跳过技术字段（exitCode、stdout、stderr 等不算报告文本）
            Set<String> skipKeys = Set.of(
                "exitCode", "exit_code", "rowCount", "durationMs", "duration_ms",
                "scriptCode", "statusCode", "executionId", "exceptionType"
            );
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (skipKeys.contains(entry.getKey())) continue;
                collectTextBlocks(entry.getValue(), blocks, seenHash);
            }
            return;
        }

        if (value instanceof List) {
            List<Object> list = (List<Object>) value;
            for (Object item : list) {
                collectTextBlocks(item, blocks, seenHash);
            }
        }
    }

    /**
     * 判断一个字符串是否为"人类可读的格式化文本"。
     * 特征：
     * 1. 长度 >= 30 字符
     * 2. 包含真实的换行符（至少 2 行）
     * 3. 看起来像自然语言文本而非纯 JSON/代码
     */
    private static boolean isFormattedText(String s) {
        if (s == null || s.length() < MIN_TEXT_LENGTH) return false;

        int newlineCount = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '\n') newlineCount++;
        }
        if (newlineCount < MIN_LINE_COUNT) return false;

        // 排除纯 JSON 字符串（以 { 或 [ 开头）
        char firstNonWhitespace = ' ';
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != ' ' && c != '\n' && c != '\r' && c != '\t') {
                firstNonWhitespace = c;
                break;
            }
        }
        if (firstNonWhitespace == '{' || firstNonWhitespace == '[') return false;

        // 排除纯代码块（连续多行以相同缩进开头，缺乏中英文混合）
        int textScore = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (isCJK(c)) textScore += 2;
            if (Character.isLetter(c) && c <= 127) textScore += 1;
            if (c == '=' || c == '-' || c == '─' || c == '┌' || c == '└' || c == '│') textScore += 1;
            if (Character.isEmoji(c) || isEmojiish(c)) textScore += 3;
        }
        // 需要足够的文本信号
        return textScore >= 10;
    }

    private static boolean isCJK(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
            || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
            || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
            || block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
            || block == Character.UnicodeBlock.GENERAL_PUNCTUATION;
    }

    private static boolean isEmojiish(char c) {
        // Emoji 通常在 Supplementary Multilingual Plane (SMP)
        // 这里用范围检测：Block Elements, Box Drawing, Geometric Shapes, Miscellaneous Symbols
        int type = Character.getType(c);
        if (type == Character.OTHER_SYMBOL || type == Character.MATH_SYMBOL) return true;
        // 补充检测：常见 emoji/symbol 范围
        return (c >= 0x2500 && c <= 0x27BF)  // Box Drawing, Miscellaneous Symbols, Dingbats
            || (c >= 0x1F300 && c <= 0x1F9FF); // Emoticons, Symbols & Pictographs
    }
}
