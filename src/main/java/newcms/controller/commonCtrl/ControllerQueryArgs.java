package newcms.controller.commonCtrl;

import com.alibaba.fastjson.JSONObject;
import newcms.base.Constant;
import org.springframework.data.domain.Sort;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 业务 Controller 通用的请求参数解析结果：searchKey / reg / sort / page / size。
 * 用 {@link #parse(JSONObject)} 或 {@link #parse(JSONObject, boolean, Set)} 从前端 requestJson 构造。
 *
 * <p>历史上 EnterpriseInfoController / InternshipTerminationController 各自实现了
 * 100+ 行结构相同的 parseQueryArgs/parseSort/QueryArgs，本类把这段逻辑抽出统一管理。</p>
 */
public final class ControllerQueryArgs {

    /** mergeRootIntoSearchKeys=true 时从根 JSON 合并到 searchKey 前要排除的元 key。 */
    private static final Set<String> RESERVED_META_KEYS = Set.of(
            "node", "reg", "pageInfo", "sort", "page", "size", "searchKey");

    private final JSONObject searchKeys;
    private final Map<String, String> regMap;
    private final Sort sort;
    private final int page;
    private final int size;

    private ControllerQueryArgs(JSONObject searchKeys, Map<String, String> regMap, Sort sort, int page, int size) {
        this.searchKeys = searchKeys;
        this.regMap = regMap;
        this.sort = sort;
        this.page = page;
        this.size = size;
    }

    public JSONObject searchKeys() {
        return searchKeys;
    }

    public Map<String, String> regMap() {
        return regMap;
    }

    public Sort sort() {
        return sort;
    }

    public int page() {
        return page;
    }

    public int size() {
        return size;
    }

    /**
     * 默认形式：只取显式的 node.searchKey / requestJson.searchKey；无回退合并根 JSON。
     */
    public static ControllerQueryArgs parse(JSONObject requestJson) {
        return parse(requestJson, false, Collections.emptySet());
    }

    /**
     * @param requestJson                前端请求 JSON
     * @param mergeRootIntoSearchKeys    true：当没有显式 searchKey 时把 requestJson/node 上的普通字段
     *                                   合并进 searchKey（去掉 RESERVED_META_KEYS 与 additionalReserved）。
     *                                   false：没有 searchKey 就返回空。
     * @param additionalReserved         合并时额外要排除的 key（例如某 Controller 自有的 "onlyMine"）。
     */
    public static ControllerQueryArgs parse(JSONObject requestJson,
                                            boolean mergeRootIntoSearchKeys,
                                            Set<String> additionalReserved) {
        JSONObject node = requestJson == null ? null : requestJson.getJSONObject("node");
        JSONObject searchKeys = parseSearchKeys(requestJson, node, mergeRootIntoSearchKeys, additionalReserved);
        Map<String, String> regMap = parseRegMap(requestJson, node);
        JSONObject pageInfo = pickJsonObject(requestJson, node, "pageInfo");
        int page = resolveInt(requestJson, node, pageInfo, "page", Constant.DEFAULT_PAGE);
        int size = resolveInt(requestJson, node, pageInfo, "size", Constant.DEFAULT_SIZE);
        Sort sort = parseSort(pickJsonObject(requestJson, node, "sort"));
        return new ControllerQueryArgs(searchKeys, regMap, sort, page, size);
    }

    private static JSONObject parseSearchKeys(JSONObject requestJson, JSONObject node,
                                              boolean mergeRootIntoSearchKeys,
                                              Set<String> additionalReserved) {
        JSONObject explicit = pickJsonObject(requestJson, node, "searchKey");
        JSONObject result = new JSONObject();
        if (explicit != null) {
            result.putAll(explicit);
            return result;
        }
        if (!mergeRootIntoSearchKeys) {
            return result;
        }
        Set<String> reserved = new HashSet<>(RESERVED_META_KEYS);
        if (additionalReserved != null) {
            reserved.addAll(additionalReserved);
        }
        if (requestJson != null) {
            for (String key : requestJson.keySet()) {
                if (!reserved.contains(key)) {
                    result.put(key, requestJson.get(key));
                }
            }
        }
        if (node != null) {
            for (String key : node.keySet()) {
                if (!reserved.contains(key)) {
                    result.put(key, node.get(key));
                }
            }
        }
        return result;
    }

    private static Map<String, String> parseRegMap(JSONObject requestJson, JSONObject node) {
        JSONObject regJson = pickJsonObject(requestJson, node, "reg");
        Map<String, String> map = new HashMap<>();
        if (regJson == null) {
            return map;
        }
        for (String key : regJson.keySet()) {
            map.put(key, regJson.getString(key));
        }
        return map;
    }

    private static JSONObject pickJsonObject(JSONObject requestJson, JSONObject node, String key) {
        if (node != null && node.getJSONObject(key) != null) {
            return node.getJSONObject(key);
        }
        if (requestJson != null && requestJson.getJSONObject(key) != null) {
            return requestJson.getJSONObject(key);
        }
        return null;
    }

    private static int resolveInt(JSONObject requestJson, JSONObject node,
                                  JSONObject pageInfo, String key, int defaultValue) {
        Integer value = pageInfo != null ? pageInfo.getInteger(key) : null;
        if (value == null && node != null) {
            value = node.getInteger(key);
        }
        if (value == null && requestJson != null) {
            value = requestJson.getInteger(key);
        }
        return value == null ? defaultValue : value;
    }

    private static Sort parseSort(JSONObject sortJson) {
        if (sortJson == null) {
            return Sort.by(Sort.Direction.DESC, "id");
        }
        String properties = sortJson.getString("properties");
        if (properties == null || properties.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "id");
        }
        Sort.Direction direction = Sort.Direction.DESC;
        String directionStr = sortJson.getString("direction");
        if (directionStr != null && !directionStr.isBlank()) {
            try {
                direction = Sort.Direction.fromString(directionStr);
            } catch (IllegalArgumentException ignored) {
                direction = Sort.Direction.DESC;
            }
        }
        return Sort.by(direction, properties.split(Constant.SPLIT_OPERATOR.COMMA));
    }
}
