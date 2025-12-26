package newcms.utils;

import newcms.base.Base;

/**
 * DAO类查找工具
 * 
 * 用于帮助CommonService通过反射查找DAO类。
 * 统一封装DAO类的查找逻辑，便于维护和扩展。
 */
public class DaoClassUtil {

    /**
     * 获取DAO类的Class对象
     * 
     * @param tblName 表名（如：BaseMain）
     * @return DAO类的Class对象
     * @throws ClassNotFoundException 如果找不到对应的DAO类
     */
    public static Class<?> getDaoClass(String tblName) throws ClassNotFoundException {
        return Class.forName(Base.repositoryPackage + tblName + "Dao");
    }
}

