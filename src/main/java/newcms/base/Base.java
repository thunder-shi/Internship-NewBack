package newcms.base;

import newcms.repository.db.BaseDepartmentDao;
import newcms.repository.db.BaseUserDao;
import newcms.repository.db.SysOssFileDao;
import newcms.repository.db.SysRoleDao;
// import newcms.service.ICommonService;
// import newcms.service.IDataListService;
// import newcms.service.IDataTreeService;
// import newcms.service.IUserService;
// import newcms.service.impl.DataTreeServiceImpl;
// import newcms.utils.FastJsonUtil;
import newcms.utils.RedisUtil;
// import newcms.utils.TreeUtil;
import com.alibaba.fastjson.JSONObject;
import org.apache.shiro.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.ObjectUtils;

import jakarta.annotation.Resource;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.lang.reflect.Field;
import java.util.*;

@Configuration
@PersistenceContext
public class Base extends Constant {
    protected Logger logger = LoggerFactory.getLogger(this.getClass());
    @Resource
    protected RedisUtil redis;
    // @Resource
    // protected ICommonService iCommonService;
    // @Resource
    // protected IDataTreeService iDataTreeService;
    // @Resource
    // protected DataTreeServiceImpl dataTreeServiceImpl;
    @Resource
    protected BaseUserDao tblUserInfoDao;
    @Resource
    protected SysRoleDao tblRoleInfoDao;
    @Resource
    protected BaseDepartmentDao tblDepartmentInfoDao;
    // @Resource
    // protected IDataListService iDataListService;
    // @Resource
    // protected IUserService iUserService;

    @Resource
    protected SysOssFileDao sysOssFileDao;


//     public void loggerRecord(String action, JSONObject obj) {
//         JSONObject json = new JSONObject();
//         json.put("userId", getLoginUserId());
//         json.put("action", action);
//         if (obj.toJSONString().length()>1000) {
//             json.put("detail", obj.toJSONString().substring(0, 1000));
//         } else {
//             json.put("detail", obj.toJSONString());
//         }
//         iCommonService.saveOneRecord("SysLogger", json);
//     }

    /**
     * 当前登录用户编号
     *
     * @return
     */
    public static Integer getLoginUserId() {
        try {
            return Integer.parseInt(SecurityUtils.getSubject().getPrincipal().toString(), 10);
        } catch (NullPointerException e) {
            throw BaseResponse.moreInfoError.error("用户Id获取失败，请重新登录后尝试！");
        }
    }

    

    // /**
    //  * checkRole(当前登录用户)
    //  * 与 @RequiresRoles 功能一致
    //  *
    //  * @param roleName
    //  * @return
    //  */
    // public static boolean checkRole(String roleName) {
    //     try {
    //         SecurityUtils.getSubject().checkRole(roleName);
    //         return true;
    //     } catch (Exception e) {
    //         return false;
    //     }
    // }

    /**
     * Demo
     *
     * @param searchKeys (fieldName,fieldValue)
     * @param regMap     (fieldName,{@link Constant#LT}{@link Constant#GT}{@link Constant#EQ}{@link Constant#LE}{@link Constant#GE}{@link Constant#NE} {@link Constant#LIKE})
     * @return
     */
    public <T> Specification<T> getSpecification(JSONObject searchKeys, Map<String, String> regMap, Map<String, Boolean> andor, Class<T> clazzInfo) {
        return (root, criteriaQuery, criteriaBuilder) -> {
            if (criteriaQuery == null) {
                return criteriaBuilder.conjunction();
            }
            List<Predicate> listAnd = Base.this.getPredicate(searchKeys, regMap, andor, clazzInfo, root, criteriaBuilder, true);
            Predicate preAnd = listAnd.isEmpty() ? null : criteriaBuilder.and(listAnd.toArray( new Predicate[listAnd.size()]));
            List<Predicate> listOr = Base.this.getPredicate(searchKeys, regMap, andor, clazzInfo, root, criteriaBuilder, false);
            Predicate preOr = listOr.isEmpty() ? null : criteriaBuilder.or(listOr.toArray( new Predicate[listOr.size()]));
            if (preAnd == null || preAnd.getExpressions().size()==0) {
                return preOr == null ? criteriaBuilder.conjunction() : criteriaQuery.where(preOr).getRestriction();
            }
            if (preOr == null || preOr.getExpressions().size()==0) {
                return criteriaQuery.where(preAnd).getRestriction();
            }
            return criteriaQuery.where(preAnd, preOr).getRestriction();
        };
    }

    public <T> List<Predicate> getPredicate(JSONObject searchKeys, Map<String, String> regMap, Map<String, Boolean> andor, Class<T> clazzInfo, Root<T> root, CriteriaBuilder criteriaBuilder, Boolean and) {
        return getPredicate(searchKeys, regMap == null ? new HashMap<String, String>(0) : regMap, andor == null ? new HashMap<String, Boolean>(0) : andor, clazzInfo, new ArrayList<>(), root, criteriaBuilder, and);
    }

    public <T> List<Predicate> getPredicate(JSONObject searchKeys, Map<String, String> regMap, Map<String, Boolean> andor, Class<?> clazzInfo, List<Predicate> list, Root<T> root, CriteriaBuilder criteriaBuilder, Boolean and) {
        Field[] fields = clazzInfo.getDeclaredFields();
        for (Field field : fields) {
            if (searchKeys.containsKey(field.getName())) {
                if ((!andor.containsKey(field.getName()) && and) || (andor.containsKey(field.getName()) && andor.get(field.getName()).equals(and))) {
                    //首先判断in
                    if (regMap.containsKey(field.getName()) && regMap.get(field.getName()).equals(Constant.IN)) {
                        String valueStr = searchKeys.getString(field.getName());
                        // 支持逗号和管道符两种分隔符：如果包含 | 就用 | 分割，否则用 , 分割
                        String delimiter = valueStr.contains("|") ? "\\|" : ",";
                        list.add(root.get(field.getName()).in(Arrays.stream(valueStr.split(delimiter)).toArray()));
                    }
                    //NOT_IN
                    else if (regMap.containsKey(field.getName()) && regMap.get(field.getName()).equals(Constant.NOT_IN)) {
                        String valueStr = searchKeys.getString(field.getName());
                        // 支持逗号和管道符两种分隔符：如果包含 | 就用 | 分割，否则用 , 分割
                        String delimiter = valueStr.contains("|") ? "\\|" : ",";
                        list.add(criteriaBuilder.not(root.get(field.getName()).in(Arrays.stream(valueStr.split(delimiter)).toArray())));
                    } else {
                        switch (field.getType().getName()) {
                            case "java.lang.String":
                                if (!ObjectUtils.isEmpty(searchKeys.getString(field.getName()))) {
                                    if (regMap.containsKey(field.getName()) && regMap.get(field.getName()).equals(Constant.LIKE)) {
                                        list.add(criteriaBuilder.like(root.get(field.getName()).as(String.class), "%" + searchKeys.getString(field.getName()) + "%"));
                                    } else if (regMap.containsKey(field.getName()) && regMap.get(field.getName()).equals(Constant.NE)) {
                                        list.add(criteriaBuilder.notEqual(root.get(field.getName()), searchKeys.getString(field.getName())));
                                    } else {
                                        list.add(criteriaBuilder.equal(root.get(field.getName()), searchKeys.getString(field.getName())));
                                    }
                                }
                                break;
                            case "java.util.Date":
//                            if (!StringUtils.isEmpty(searchKeys.getJSONObject(field.getName()))) {
//                                JSONObject betweenDate = searchKeys.getJSONObject(field.getName());
//                                list.add(criteriaBuilder.between(root.get(field.getName()).as(field.getType()), betweenDate.getDate("beginTime"), betweenDate.getDate("endTime")));
//                            }
//                                if (!StringUtils.isEmpty(searchKeys.getDate(field.getName()))) {
                                    if (regMap.containsKey(field.getName())) {
                                        switch (regMap.get(field.getName())) {
                                            case Constant.LT:
                                                list.add(criteriaBuilder.lessThan((Expression<Date>) root.get(field.getName()).as(Date.class), searchKeys.getDate(field.getName())));
                                                break;
                                            case Constant.GT:
                                                list.add(criteriaBuilder.greaterThan((Expression<Date>) root.get(field.getName()).as(Date.class), searchKeys.getDate(field.getName())));
                                                break;
                                            case Constant.LE:
                                                list.add(criteriaBuilder.lessThanOrEqualTo((Expression<Date>) root.get(field.getName()).as(Date.class), searchKeys.getDate(field.getName())));
                                                break;
                                            case Constant.EQ:
                                                list.add(criteriaBuilder.equal(root.get(field.getName()), searchKeys.getDate(field.getName())));
                                                break;
                                            case Constant.GE:
                                                list.add(criteriaBuilder.greaterThanOrEqualTo((Expression<Date>) root.get(field.getName()).as(Date.class), searchKeys.getDate(field.getName())));
                                                break;
                                            case Constant.NE:
                                                list.add(criteriaBuilder.notEqual(root.get(field.getName()), searchKeys.getDate(field.getName())));
                                                break;
                                            case Constant.RANGE:
                                                JSONObject obj = searchKeys.getJSONObject(field.getName());
                                                list.add(criteriaBuilder.between((Expression<Date>) root.get(field.getName()).as(Date.class), obj.getDate("beginDate"), obj.getDate("endDate")));
                                                break;
                                            default:
                                                break;
                                        }
                                    } else {
                                        list.add(criteriaBuilder.equal(root.get(field.getName()), searchKeys.getDate(field.getName())));
                                    }
//                                }
                                break;
                            case "java.lang.Byte":
                            case "java.lang.Short":
                            case "java.lang.Integer":
                            case "java.lang.Long":
                            case "java.lang.Double":
                            case "java.lang.Float":
                                if (!ObjectUtils.isEmpty(searchKeys.getInteger(field.getName()))) {
                                    if (regMap.containsKey(field.getName())) {
                                        switch (regMap.get(field.getName())) {
                                            case Constant.LT:
                                                list.add(criteriaBuilder.lessThan((Expression<Integer>) root.get(field.getName()).as(Integer.class), searchKeys.getInteger(field.getName())));
                                                break;
                                            case Constant.GT:
                                                list.add(criteriaBuilder.greaterThan((Expression<Integer>) root.get(field.getName()).as(Integer.class), searchKeys.getInteger(field.getName())));
                                                break;
                                            case Constant.LE:
                                                list.add(criteriaBuilder.lessThanOrEqualTo((Expression<Integer>) root.get(field.getName()).as(Integer.class), searchKeys.getInteger(field.getName())));
                                                break;
                                            case Constant.EQ:
                                                list.add(criteriaBuilder.equal(root.get(field.getName()), searchKeys.getString(field.getName())));
                                                break;
                                            case Constant.GE:
                                                list.add(criteriaBuilder.greaterThanOrEqualTo((Expression<Integer>) root.get(field.getName()).as(Integer.class), searchKeys.getInteger(field.getName())));
                                                break;
                                            case Constant.NE:
                                                list.add(criteriaBuilder.notEqual(root.get(field.getName()), searchKeys.getString(field.getName())));
                                                break;
                                            default:
                                                break;
                                        }
                                    } else {
                                        list.add(criteriaBuilder.equal(root.get(field.getName()), searchKeys.getString(field.getName())));
                                    }
                                }
                                break;
                            case "java.lang.Boolean":
                                list.add(criteriaBuilder.equal((Expression<Boolean>) root.get(field.getName()).as(Boolean.class), searchKeys.getBoolean(field.getName())));
                                break;
                            case "java.time.LocalDateTime":
                                String dateTimeStr = searchKeys.getString(field.getName());
                                if (dateTimeStr != null && !dateTimeStr.isEmpty()) {
                                    java.time.LocalDateTime dateTime = java.time.LocalDateTime.parse(
                                            dateTimeStr.replace(" ", "T"));
                                    if (regMap != null && regMap.containsKey(field.getName())) {
                                        switch (regMap.get(field.getName())) {
                                            case Constant.LT:
                                                list.add(criteriaBuilder.lessThan(root.get(field.getName()), dateTime));
                                                break;
                                            case Constant.GT:
                                                list.add(criteriaBuilder.greaterThan(root.get(field.getName()), dateTime));
                                                break;
                                            case Constant.LE:
                                                list.add(criteriaBuilder.lessThanOrEqualTo(root.get(field.getName()), dateTime));
                                                break;
                                            case Constant.GE:
                                                list.add(criteriaBuilder.greaterThanOrEqualTo(root.get(field.getName()), dateTime));
                                                break;
                                            default:
                                                list.add(criteriaBuilder.equal(root.get(field.getName()), dateTime));
                                                break;
                                        }
                                    } else {
                                        list.add(criteriaBuilder.equal(root.get(field.getName()), dateTime));
                                    }
                                }
                                break;
                            default:
                                logger.warn("<WARN>  [{}] 类型未补全 fieldTypeName [{}],fieldName [{}],fieldValue [{}]", clazzInfo.getName(), field.getType().getName(), field.getName(), searchKeys.get(field.getName()));
                                break;
                        }
                    }
                }
            }
        }
        Class<?> superClass = clazzInfo.getSuperclass();
        return superClass != null && superClass.getDeclaredFields().length != 0 ? getPredicate(searchKeys, regMap, andor, superClass, list, root, criteriaBuilder, and) : list;
    }
}



