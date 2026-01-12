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

    @Override
    @SuppressWarnings("null")
    public Object saveOneRecord(String tblName, JSONObject json) {
        try {
            Class<?> clazzDao = DaoClassUtil.getDaoClass(tblName);
            Object beanDao = applicationContext.getBean(tblName.substring(0, 1).toLowerCase() + tblName.substring(1) + "Dao", clazzDao);
            if ( json.keySet().contains("id") && json.get("id")!=null && !json.get("id").equals(0)) {
                json.put("updateTime", DateUtil.format(Date.from(Instant.now()), "yyyy-MM-dd HH:mm:ss"));
            }
            return clazzDao.getMethod("save", Object.class).invoke(beanDao, FastJsonUtil.toJavaObject(json, Class.forName(Base.entityPackage + tblName)));
        } catch (ClassNotFoundException e) {
            LogUtil.error(logger, e);
            throw BaseResponse.moreInfoError.error("tblName 异常[" + tblName + "]: " + e.getMessage());
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ex) {
            LogUtil.error(logger, ex);
            throw BaseResponse.moreInfoError.error("反射处理失败[" + tblName + "]: " + ex.getMessage());
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
            throw BaseResponse.moreInfoError.error("反射处理失败[" + tblName + "]: " + ex.getMessage());
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
}
