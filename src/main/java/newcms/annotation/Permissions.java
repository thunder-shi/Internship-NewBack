package newcms.annotation;

import newcms.base.Constant;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Permissions {
    /**
     * 模块标识
     * @return
     */
    String value();
    /**
     *  || &&
     *  CRUD 的或与 关系
     * {@link com.ahaxt.competition.base.Constant#OR}
     * {@link com.ahaxt.competition.base.Constant#AND}
     *
     * @return
     */
    String orAndNon() default  Constant.OR;

    /**
     * 新增权限
     * @return
     */
    boolean c() default false;

    /**
     * 查询权限
     * @return
     */
    boolean r() default true;

    /**
     * 修改权限
     * @return
     */
    boolean u() default false;

    /**
     * 删除权限
     * @return
     */
    boolean d() default false;

}
