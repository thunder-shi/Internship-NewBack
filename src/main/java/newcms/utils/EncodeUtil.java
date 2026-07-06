package newcms.utils;

import org.apache.shiro.crypto.hash.SimpleHash;
import java.util.HashMap;
import java.util.Map;

/**
 * @author hongzhangming
 */
public class EncodeUtil {
    private static final String INITIAL_PASSWORD_PREFIX = "SLSDsx#";
    private static final int MIN_PASSWORD_LENGTH = 8;

    /**
     * shiro 加密方式
     * @param source password
     * @param salt   userId
     * @return
     */
    public static String pwdShiro(String source, Object salt) {
//        if (!isStrongPwd(source)) {
//            throw BaseResponse.weakPassword.error();
//        }
        return new SimpleHash("md5", source, salt.toString().getBytes(), 3).toHex();
    }


    // /**
    //  * md5 加密
    //  * @param param
    //  * @param salt
    //  * @return
    //  * @throws UnsupportedEncodingException
    //  */
    // private static String encode(String param, Object salt) throws UnsupportedEncodingException {
    //     return DigestUtils.md5DigestAsHex((param + salt).getBytes(StandardCharsets.UTF_8));
    // }

    public static boolean isStrongPwd(String password) {
        Map<String, String> map = new HashMap<>(3);
        for (int i = 0; i < password.length(); i++) {
            int A = password.charAt(i);
            if (A >= 48 && A <= 57) {
                map.put("数字", "数字");
            } else if ((A >= 65 && A <= 90) || (A >= 97 && A <= 122)) {
                map.put("字母", "字母");
            } else {
                map.put("特殊", "特殊");
            }
        }
        return map.keySet().size() >= 3 && password.length() >= MIN_PASSWORD_LENGTH;
    }

    /**
     * 根据学工号生成初始/重置密码：SLSDsx# + 学工号后四位（不足四位左补 0）。
     */
    public static String buildInitialPasswordFromWorkId(String workId) {
        if (workId == null || workId.trim().isEmpty()) {
            throw new IllegalArgumentException("学工号为空，无法生成初始密码");
        }
        String normalized = workId.trim();
        String lastFour;
        if (normalized.length() >= 4) {
            lastFour = normalized.substring(normalized.length() - 4);
        } else {
            lastFour = String.format("%4s", normalized).replace(' ', '0');
        }
        return INITIAL_PASSWORD_PREFIX + lastFour;
    }
}
