package newcms.service.impl;

import newcms.base.Base;
import newcms.base.BaseResponse;
import newcms.service.ICommonService;
import newcms.utils.DaoClassUtil;
import newcms.utils.DateUtil;
import newcms.utils.FastJsonUtil;
import newcms.utils.LogUtil;
import com.alibaba.fastjson.JSONObject;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;

import jakarta.annotation.Resource;
import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.util.*;

@Service
@Transactional(rollbackFor = Exception.class)
public class CommonServiceImpl extends Base implements ICommonService {
    @Resource
    private ApplicationContext applicationContext;


    //region getOneRecordById
    /**
     * 根据主键获取一条记录
     * @param tblName 操作表名
     * @param id      主键id，object形式传入，可以是整型或者字符型
     * @return
     */
    @Override
    public Object getOneRecordById(String tblName, Object id) {
        return getOneRecordById(tblName, id, false);
    }

    /**
     * 根据主键获取一条记录
     * @param tblName 操作表名
     * @param id      主键id，object形式传入，可以是整型或者字符型
     * @param delFlag 是否考虑伪删除
     * @return
     */
    @Override
    @SuppressWarnings("null")
    public Object getOneRecordById(String tblName, Object id, Boolean delFlag) {
        try {
            Class<?> clazzDao = DaoClassUtil.getDaoClass(tblName);
            Object beanDao = applicationContext.getBean(tblName.substring(0, 1).toLowerCase() + tblName.substring(1) + "Dao", clazzDao);
            if (delFlag) {
                return clazzDao.getMethod("getOne", Object.class).invoke(beanDao, (Integer) id);
            } else {
                return clazzDao.getMethod("getByIdAndIsDeletedFalse", Object.class).invoke(beanDao, (Integer) id);
            }
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException ex) {
            LogUtil.error(logger, ex);
            throw BaseResponse.moreInfoError.error("反射处理失败[" + tblName + "]: " + ex.getMessage());
        }
    }
    //endregion

    //region getOneRecordByCode
    /**
     * 根据code获取一条记录
     * @param tblName 操作表名
     * @param code    编码
     * @param delFlag 是否考虑伪删除
     * @return
     */
    @Override
    @SuppressWarnings("null")
    public Object getOneRecordByCode(String tblName, String code, Boolean delFlag) {
        try {
            Class<?> clazzDao = DaoClassUtil.getDaoClass(tblName);
            Object beanDao = applicationContext.getBean(tblName.substring(0, 1).toLowerCase() + tblName.substring(1) + "Dao", clazzDao);
            if (delFlag) {
                return clazzDao.getMethod("getByCode", String.class).invoke(beanDao, code);
            } else {
                return clazzDao.getMethod("getByCodeAndIsDeletedFalse", String.class).invoke(beanDao, code);
            }
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException ex) {
            LogUtil.error(logger, ex);
            throw BaseResponse.moreInfoError.error("反射处理失败[" + tblName + "]: " + ex.getMessage());
        }
    }
    //endregion

    //region getRecordsByIds
    /**
     * 根据一组主键Id获得对应的多条记录
     * @param tblName 表名
     * @param ids     一组id
     * @return
     */
    @Override
    public Object getRecordsByIds(String tblName, Set<Integer> ids) {
        return getRecordsByIds(tblName, ids, false);
    }

    /**
     * 根据一组主键Id获得对应的多条记录
     * @param tblName 表名
     * @param ids     一组id
     * @return
     * @parem delFlag 是否考虑伪删除，默认考虑
     */
    @Override
    @SuppressWarnings("null")
    public Object getRecordsByIds(String tblName, Set<Integer> ids, Boolean delFlag) {
        try {
            Class<?> clazzDao = DaoClassUtil.getDaoClass(tblName);
            Object beanDao = applicationContext.getBean(tblName.substring(0, 1).toLowerCase() + tblName.substring(1) + "Dao", clazzDao);

            if (delFlag) {
                return clazzDao.getMethod("findByIdIn", Iterable.class).invoke(beanDao, ids);
            } else {
                return clazzDao.getMethod("findByIdInAndIsDeletedFalse", Iterable.class).invoke(beanDao, ids);
            }
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException ex) {
            LogUtil.error(logger, ex);
            throw BaseResponse.moreInfoError.error("反射处理失败[" + tblName + "]: " + ex.getMessage());
        }
    }
    //endregion

    //region getSomeRecords
    @Override
    public Object getSomeRecords(String tblName) {
        JSONObject json = new JSONObject();
        return getSomeRecords(tblName, json);
    }

    @Override
    public Object getSomeRecords(String tblName, JSONObject searchKeys) {
        return getSomeRecords(tblName, searchKeys, null);
    }

    @Override
    public Object getSomeRecords(String tblName, JSONObject searchKeys, Map<String, String> repMap) {
        Sort sort = Sort.by(Sort.Direction.DESC, "id");
        return getSomeRecords(tblName, searchKeys, repMap, sort);
    }

    @Override
    public Object getSomeRecords(String tblName, JSONObject searchKeys, Map<String, String> repMap, Sort sort) {
        return getSomeRecords(tblName, searchKeys, repMap, sort, null, null);
    }

    @Override
    public Object getSomeRecords(String tblName, JSONObject searchKeys, Map<String, String> repMap, Sort sort, Integer page, Integer size) {
        return getSomeRecords(tblName, searchKeys, repMap, sort, page, size, false);
    }

    @Override
    public Object getSomeRecords(String tblName, JSONObject searchKeys, Map<String, String> repMap, Sort sort, Integer page, Integer size, Boolean delFlag) {
        return getSomeRecords(tblName, searchKeys, repMap, sort, page, size, delFlag, null);
    }
    /**
     * 注意：json中isDeleted字段设置了默认值false，若想查全部需设置 json.put("isDeleted":"");
     * @param tblName
     * @param searchKeys
     * @param page
     * @param size
     * @param sort
     * @return
     */
    @Override
    @SuppressWarnings("null")
    public Object getSomeRecords(String tblName, JSONObject searchKeys, Map<String, String> repMap, Sort sort, Integer page, Integer size, Boolean delFlag, Map<String, Boolean> andor) {
        Object ret;
        if (searchKeys != null) {
            // 过滤掉空内容
            JSONObject searchKey = new JSONObject();
            searchKey.putAll(searchKeys);
            for (Map.Entry<String, Object> entry : searchKey.entrySet()) {
                if (ObjectUtils.isEmpty(entry.getValue())) {
                    if (repMap != null) {
                        repMap.remove(entry.getKey());
                    }
                    searchKeys.remove(entry.getKey());
                }
            }
        }
        else {
            searchKeys = new JSONObject();
        }
        if (!delFlag) {
            searchKeys.put("isDeleted", 0);
        }
        try {
            Class<?> clazzDao = DaoClassUtil.getDaoClass(tblName);
            Class<?> clazzInfo = Class.forName(Base.entityPackage + tblName);

            // 注意：这里保留了你原来的 Bean 获取逻辑，虽然可能有大小写隐患，但先不动它
            Object beanDao = applicationContext.getBean(tblName.substring(0, 1).toLowerCase() + tblName.substring(1) + "Dao", clazzDao);

            if (page == null || size == null || size == -1) {
                ret = clazzDao.getMethod("findAll", Specification.class, Pageable.class).invoke(beanDao, super.getSpecification(searchKeys, repMap, andor, clazzInfo), PageRequest.of(0, 10000, sort));
            } else {
                ret = clazzDao.getMethod("findAll", Specification.class, Pageable.class).invoke(beanDao, super.getSpecification(searchKeys, repMap, andor, clazzInfo), PageRequest.of(page - 1, size, sort));
            }
            return ret;
        } catch (Exception ex) {
            LogUtil.error(logger, ex);
            
            // 获取详细的错误信息
            String errorMsg = ex.getMessage();
            String errorType = ex.getClass().getSimpleName();
            
            // 如果是 InvocationTargetException，获取底层异常的原因
            if (ex instanceof java.lang.reflect.InvocationTargetException) {
                Throwable cause = ((java.lang.reflect.InvocationTargetException) ex).getTargetException();
                if (cause != null) {
                    errorType = cause.getClass().getSimpleName();
                    errorMsg = cause.getMessage();
                    // 如果底层异常还有原因，也加上
                    if (cause.getCause() != null) {
                        errorMsg += " (原因: " + cause.getCause().getMessage() + ")";
                    }
                }
            }
            
            throw BaseResponse.moreInfoError.error("查询出错[" + tblName + "]: " + errorType + " - " + (errorMsg != null ? errorMsg : "未知错误"));
        }
    }
    //endregion

    /**
     * 读取现有记录并合并更新字段
     * @param clazzDao Dao类
     * @param beanDao Dao实例
     * @param clazzInfo 实体类
     * @param json 更新的JSONObject
     * @return 合并后的实体对象
     * @throws NoSuchMethodException
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     */
    private Object mergeExistingRecord(Class<?> clazzDao, Object beanDao, Class<?> clazzInfo, JSONObject json) 
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        // 查询现有记录（findById 返回 Optional<T>，需要解包）
        Object findResult = clazzDao.getMethod("findById", Object.class).invoke(beanDao, json.get("id"));
        Object existingEntity = (findResult instanceof Optional) ? ((Optional<?>) findResult).orElse(null) : findResult;
        if (existingEntity == null) {
            throw BaseResponse.moreInfoError.error("记录不存在[id=" + json.get("id") + "]");
        }

        // 将现有实体转换为 JSONObject
        JSONObject existingJson = FastJsonUtil.toJson(existingEntity);
        
        // 只更新 JSONObject 中存在的字段（排除 null 值和空字符串）
        for (String key : json.keySet()) {
            Object value = json.get(key);
            // 如果值为 null 或空字符串，跳过（不更新该字段）
            // 如果需要将字段设置为 null，可以显式传递 null
            if (value != null && !(value instanceof String && ((String) value).isEmpty())) {
                existingJson.put(key, value);
            }
        }
        
        // 设置更新时间
        existingJson.put("updateTime", DateUtil.format(Date.from(Instant.now()), "yyyy-MM-dd HH:mm:ss"));
        
        // 转换回实体对象（使用合并后的 existingJson）
        return existingJson.toJavaObject(clazzInfo);
    }

    @Override
    @SuppressWarnings("null")
    public Object saveOneRecord(String tblName, JSONObject json) {
        if (json == null) {
            throw BaseResponse.parameterInvalid.error("json 参数不能为空");
        }
        try {
            Class<?> clazzDao = DaoClassUtil.getDaoClass(tblName);
            Class<?> clazzInfo = Class.forName(Base.entityPackage + tblName);
            Object beanDao = applicationContext.getBean(tblName.substring(0, 1).toLowerCase() + tblName.substring(1) + "Dao", clazzDao);
            
            Object entity;
            // 判断是新增还是更新
            if (json.containsKey("id") && json.get("id") != null && !json.get("id").equals(0)) {
                // 更新操作：读取现有记录并合并字段
                entity = mergeExistingRecord(clazzDao, beanDao, clazzInfo, json);
            } else {
                // 新增操作：直接转换
                entity = json.toJavaObject(clazzInfo);
            }
        
            return clazzDao.getMethod("save", Object.class).invoke(beanDao, entity);
        } catch (ClassNotFoundException e) {
            LogUtil.error(logger, e);
            throw BaseResponse.moreInfoError.error("tblName 异常[" + tblName + "]: " + e.getMessage());
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ex) {
            LogUtil.error(logger, ex);
            Throwable cause = (ex instanceof InvocationTargetException) ? ex.getCause() : ex;
            String detail = (cause != null && cause.getMessage() != null) ? cause.getMessage() : String.valueOf(ex);
            throw BaseResponse.moreInfoError.error("反射处理失败[" + tblName + "]: " + detail);
        }
    }

    @Override
    @SuppressWarnings("null")
    public Object saveSomeRecords(String tblName, Iterable<?> iterable) {
        try {
            Class<?> clazzDao = DaoClassUtil.getDaoClass(tblName);
            Class<?> clazzInfo = Class.forName(Base.entityPackage + tblName);
            Object beanDao = applicationContext.getBean(tblName.substring(0, 1).toLowerCase() + tblName.substring(1) + "Dao", clazzDao);

            List<Object> list = new ArrayList<>();
            Iterator<?> iterator = iterable.iterator();
            while (iterator.hasNext()) {
                list.add(FastJsonUtil.parseObject(FastJsonUtil.toString(iterator.next()), clazzInfo));
            }
            return clazzDao.getMethod("saveAll", Iterable.class).invoke(beanDao, list);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException ex) {
            LogUtil.error(logger, ex);
            Throwable cause = (ex instanceof InvocationTargetException) ? ex.getCause() : ex;
            String detail = (cause != null && cause.getMessage() != null) ? cause.getMessage() : String.valueOf(ex);
            throw BaseResponse.moreInfoError.error("反射处理失败[" + tblName + "]: " + detail);
        }
    }

    /**
     * 真删除
     * @param tblName
     * @param id
     * @return
     */
    @Override
    public Object deleteRecord(String tblName, Integer id) {
        try {
            Class<?> clazzDao = DaoClassUtil.getDaoClass(tblName);
            Object beanDao = applicationContext.getBean(tblName.substring(0, 1).toLowerCase() + tblName.substring(1) + "Dao", clazzDao);

            return clazzDao.getMethod("deleteById", Object.class).invoke(beanDao, id);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException ex) {
            LogUtil.error(logger, ex);
            throw BaseResponse.moreInfoError.error("反射处理失败[" + tblName + "]: " + ex.getMessage());
        }
    }

    @Override
    @SuppressWarnings("null")
    public Object deleteRecordByDelflag(String tblName, Integer id) {
        try {
            Class<?> clazzDao = DaoClassUtil.getDaoClass(tblName);
            Object beanDao = applicationContext.getBean(tblName.substring(0, 1).toLowerCase() + tblName.substring(1) + "Dao", clazzDao);

            return clazzDao.getMethod("deleteByIsDeleted", Object.class).invoke(beanDao, id);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException ex) {
            LogUtil.error(logger, ex);
            throw BaseResponse.moreInfoError.error("反射处理失败[" + tblName + "]: " + ex.getMessage());
        }
    }

    /**
     * 清空（（已删除的）
     * @param tblName
     * @return
     */
    @Override
    @SuppressWarnings("null")
    public Object deleteRecordsByDelflag(String tblName) {
        try {
            Class<?> clazzDao = DaoClassUtil.getDaoClass(tblName);
            Object beanDao = applicationContext.getBean(tblName.substring(0, 1).toLowerCase() + tblName.substring(1) + "Dao", clazzDao);
            return clazzDao.getMethod("deleteByIsDeleted").invoke(beanDao);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException ex) {
            LogUtil.error(logger, ex);
            throw BaseResponse.moreInfoError.error("反射处理失败[" + tblName + "]: " + ex.getMessage());
        }
    }

    /**
     * 通过id删除多个
     * @param tblName
     * @param ids
     * @return
     */

    @Override
    public Object deleteSomeRecords(String tblName, List<Integer> ids) {
        try {
            Class<?> clazzDao = DaoClassUtil.getDaoClass(tblName);
            Object beanDao = applicationContext.getBean(tblName.substring(0, 1).toLowerCase() + tblName.substring(1) + "Dao", clazzDao);

            return clazzDao.getMethod("deleteByIdIn", Iterable.class).invoke(beanDao, ids);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException ex) {
            LogUtil.error(logger, ex);
            throw BaseResponse.moreInfoError.error("反射处理失败[" + tblName + "]: " + ex.getMessage());
        }
    }

    /**
     * 根据查询条件逻辑删除多条记录
     * @param tblName 表名
     * @param searchKeys 查询条件
     * @param repMap 查询操作符映射（如：LT, GT, EQ, LIKE, IN等）
     * @param andor 查询条件AND/OR映射
     * @return 删除的记录数量
     */
    @Override
    @SuppressWarnings("null")
    public Object deleteSomeRecords(String tblName, JSONObject searchKeys, Map<String, String> repMap, Map<String, Boolean> andor) {
        // 处理查询条件，过滤空值（参考 getSomeRecords 的实现）
        if (searchKeys != null) {
            JSONObject searchKey = new JSONObject();
            searchKey.putAll(searchKeys);
            for (Map.Entry<String, Object> entry : searchKey.entrySet()) {
                if (ObjectUtils.isEmpty(entry.getValue())) {
                    if (repMap != null) {
                        repMap.remove(entry.getKey());
                    }
                    searchKeys.remove(entry.getKey());
                }
            }
        } else {
            searchKeys = new JSONObject();
        }

        try {
            Class<?> clazzDao = DaoClassUtil.getDaoClass(tblName);
            Class<?> clazzInfo = Class.forName(Base.entityPackage + tblName);
            Object beanDao = applicationContext.getBean(tblName.substring(0, 1).toLowerCase() + tblName.substring(1) + "Dao", clazzDao);

            // 使用 Specification 查询符合条件的记录（查询所有，不限制数量）
            Object pageResult = clazzDao.getMethod("findAll", Specification.class, Pageable.class).invoke(
                    beanDao, 
                    super.getSpecification(searchKeys, repMap, andor, clazzInfo), 
                    PageRequest.of(0, 10000, Sort.unsorted())
            );

            // 获取查询结果列表
            List<?> contentList = (List<?>) pageResult.getClass().getMethod("getContent").invoke(pageResult);
            
            if (contentList == null || contentList.isEmpty()) {
                return 0; // 没有找到符合条件的记录
            }

            // 遍历结果，设置 isDeleted = true，更新 updateTime
            List<Object> updatedEntities = new ArrayList<>();
            for (Object entity : contentList) {
                // 将实体转换为 JSONObject
                JSONObject entityJson = FastJsonUtil.toJson(entity);
                // 设置 isDeleted = true（逻辑删除）
                entityJson.put("isDeleted", true);
                // 更新 updateTime（使用 Date 对象）
                entityJson.put("updateTime", Date.from(Instant.now()));
                // 转换回实体对象
                Object updatedEntity = entityJson.toJavaObject(clazzInfo);
                updatedEntities.add(updatedEntity);
            }

            // 批量保存更新后的记录
            clazzDao.getMethod("saveAll", Iterable.class).invoke(beanDao, updatedEntities);

            return updatedEntities.size(); // 返回删除的记录数量
        } catch (Exception ex) {
            LogUtil.error(logger, ex);
            
            // 获取详细的错误信息
            String errorMsg = ex.getMessage();
            String errorType = ex.getClass().getSimpleName();
            
            // 如果是 InvocationTargetException，获取底层异常的原因
            if (ex instanceof InvocationTargetException) {
                Throwable cause = ((InvocationTargetException) ex).getTargetException();
                if (cause != null) {
                    errorType = cause.getClass().getSimpleName();
                    errorMsg = cause.getMessage();
                    if (cause.getCause() != null) {
                        errorMsg += " (原因: " + cause.getCause().getMessage() + ")";
                    }
                }
            }
            
            throw BaseResponse.moreInfoError.error("删除出错[" + tblName + "]: " + errorType + " - " + (errorMsg != null ? errorMsg : "未知错误"));
        }
    }
}
