package newcms.utils;
import jakarta.annotation.Resource;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.SecretKeySpec;

import com.alibaba.fastjson.JSONObject;
import newcms.base.BaseResponse;
import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Component
public class EncryptUtil {

    private static final Logger logger = LoggerFactory.getLogger(EncryptUtil.class);

    @Resource
    protected RedisUtil redis;


    private final String KEY = "abcdefgabcdefg12";
    private final String ALGORITHMSTR = "AES/ECB/PKCS5Padding";

    // Key 过期时间（毫秒）
    private static final long KEY_EXPIRE_TIME = 300000;

    // 添加读写锁防止并发问题
    private static final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public static List<String> allKeys = new ArrayList<>();
    public static List<Boolean> haveKeys = new ArrayList<>();
    public static List<Instant> KeysDate = new ArrayList<>();

    private Integer getSuitablePos() {
        // 此方法应在持有写锁时调用
        Integer ret = -1;
        boolean flag = false;
        int i=0;
        Instant nowDate = Instant.now();
        while (i<haveKeys.size() && !flag) {
            Instant keyDate = KeysDate.get(i);
            long ll = nowDate.toEpochMilli() - keyDate.toEpochMilli();
            if (ll > KEY_EXPIRE_TIME || !haveKeys.get(i)) {
                ret  = i;
                flag = true;
            } else {
                i++;
            }
        }
        return ret;
    }


    public JSONObject setAllKeys(String key) {
        lock.writeLock().lock();
        try {
            Integer pos = getSuitablePos();
            if (pos == -1) {
                allKeys.add(key);
                haveKeys.add(true);
                KeysDate.add(Instant.now());
                JSONObject val = new JSONObject();
                val.put("key", key);
                val.put("value", allKeys.size()-1);
                return val;
            } else {
                allKeys.set(pos, key);  // 更新 key
                haveKeys.set(pos, true);
                KeysDate.set(pos, Instant.now());
                JSONObject val = new JSONObject();
                val.put("key", key);
                val.put("value", pos);
                return val;
            }
        } finally {
            lock.writeLock().unlock();
        }
    }


    public String base64Encode(byte[] bytes){
        return Base64.encodeBase64String(bytes);
    }
    public byte[] base64Decode(String base64Code) throws Exception{
        return Base64.decodeBase64(base64Code);
    }
    public byte[] aesEncryptToBytes(String content, String encryptKey) throws Exception {
        KeyGenerator kgen = KeyGenerator.getInstance("AES");
        kgen.init(128);
        Cipher cipher = Cipher.getInstance(ALGORITHMSTR);
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encryptKey.getBytes(), "AES"));

        return cipher.doFinal(content.getBytes(StandardCharsets.UTF_8));
    }
    public String aesEncrypt(String content, String encryptKey) throws Exception {
        return base64Encode(aesEncryptToBytes(content, encryptKey));
    }
    public String aesDecryptByBytes(byte[] encryptBytes, String decryptKey) throws Exception {
        KeyGenerator kgen = KeyGenerator.getInstance("AES");
        kgen.init(128);

        Cipher cipher = Cipher.getInstance(ALGORITHMSTR);
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(decryptKey.getBytes(), "AES"));
        byte[] decryptBytes = cipher.doFinal(encryptBytes);

        return new String(decryptBytes, StandardCharsets.UTF_8);
    }
    public String aesDecrypt(String encryptStr, String decryptKey) throws Exception {
        return aesDecryptByBytes(base64Decode(encryptStr), decryptKey);
    }

    public String getKeyWord(String keyWord) {
        if (keyWord == null || keyWord.length() < 2) {
            logger.error("keyWord 为空或长度不足: {}", keyWord);
            throw BaseResponse.moreInfoError.error("keyWord 无效");
        }

        int value;
        try {
            value = Integer.parseInt(keyWord.substring(keyWord.length() - 2));
        } catch (NumberFormatException e) {
            logger.error("keyWord 后缀解析失败: {}", keyWord);
            throw BaseResponse.moreInfoError.error("keyWord 格式错误");
        }

        lock.writeLock().lock();
        try {
            // 检查索引是否有效
            if (value < 0 || value >= allKeys.size()) {
                logger.error("keyWord 索引越界: value={}, allKeys.size={}", value, allKeys.size());
                throw BaseResponse.moreInfoError.error("密钥索引无效，请刷新页面重新获取");
            }

            Instant nowDate = Instant.now();
            Instant keyDate = KeysDate.get(value);
            long elapsed = nowDate.toEpochMilli() - keyDate.toEpochMilli();

            // 检查 key 是否已被使用
            if (!haveKeys.get(value)) {
                logger.warn("keyWord 对应的密钥已被使用: value={}", value);
                throw BaseResponse.moreInfoError.error("密钥已被使用，请刷新页面重新获取");
            }

            // 检查 key 是否已过期
            if (elapsed >= KEY_EXPIRE_TIME) {
                logger.warn("keyWord 对应的密钥已过期: value={}, elapsed={}ms", value, elapsed);
                throw BaseResponse.moreInfoError.error("密钥已过期，请刷新页面重新获取");
            }

            try {
                String key;
                if (value < 10) {
                    key = allKeys.get(value) + "0" + value + (new StringBuilder(allKeys.get(value)).reverse().toString());
                } else {
                    key = allKeys.get(value) + value + (new StringBuilder(allKeys.get(value)).reverse().toString());
                }
                String decryptData = aesDecrypt(keyWord.substring(0, keyWord.length() - 2), key);

                // 用后即焚
                haveKeys.set(value, false);

                return decryptData;
            } catch (Exception ex) {
                logger.error("解密失败: keyWord={}, value={}", keyWord, value, ex);
                throw BaseResponse.moreInfoError.error("解密失败: " + ex.getMessage());
            }
        } finally {
            lock.writeLock().unlock();
        }
    }


}
