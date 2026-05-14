package newcms.base;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Constant {
    /**
     * orAndNon
     */
    public static final String OR = "||";
    public static final String AND = "&&";
    public static final String NOT = "!";
    public static final String LT = "<";
    public static final String GT = ">";
    public static final String EQ = "=";
    public static final String LE = "<=";
    public static final String GE = ">=";
    public static final String NE = "!=";
    public static final String LIKE = "≈";
    public static final String IN = "()";
    public static final String NOT_IN = "!()";
    public static final String FIND_IN = "fi()";
    public static final String RANGE = "<=>";
    /**
     * APPLICATION_NAME 项目名
     * FILE_SEPARATOR 自定义统一文本分隔符
     */
    public static String APPLICATION_NAME, FILE_SEPARATOR;

    @Value("${spring.application.name}")
    public void setApplicationName(String applicationName) {
        APPLICATION_NAME = applicationName;
        FILE_SEPARATOR = "@" + applicationName + "@";
    }

    /**
     * AXT-PUBLIC ossBucketName
     */
    public static String AXT_PUBLIC;

//    @Value("${oss.bucketName.public}")
//    public void setBucketName(String bucketName) {
//        AXT_PUBLIC = bucketName;
//    }
    /**
     * entityPackage
     * repositoryPackage
     */
    public static String entityPackage, repositoryPackage;

    @Value(value = "${entity.package}")
    public void setEntityPackage(String entPackage) {
        entityPackage = entPackage;
    }

    @Value(value = "${repository.package}")
    public void setRepositoryPackage(String repPackage) {
        repositoryPackage = repPackage;
    }

    /**
     * permission suffix
     */
    public static final String CREATE = ":c";
    public static final String RETRIEVE = ":r";
    public static final String UPDATE = ":u";
    public static final String DELETE = ":d";



    /**
     * Regular expression
     */
    public static final String
            REGEX_MOBILE = "^1[3-9]\\d{9}$",
            REGEX_EMAIL = "^\\w+((-\\w+)|(\\.\\w+))*\\@[A-Za-z0-9]+((\\.|-)[A-Za-z0-9]+)*\\.[A-Za-z0-9]+$";


    public static final int  DEFAULT_PAGE = 1;
    public static final int DEFAULT_SIZE = 25;

    public static final String USER_INFO = "BaseUser"; //用户表
    public static final String DEPARTMENT_INFO = "BaseDepartment"; //部门信息表
    public static final String OSS_FILE_INFO = "SysOssFile"; //OSS表

    /**
     * oss文件类型
     */
    public static final int HEAD_IMAGE = 1;


    public static final class SPLIT_OPERATOR {
        public static final String VERTICALLINE = "|";
        public static final String COMMA =  ",";
        public static final String DOT = ".";
        public static final String AND = "&";
    }
    // 角色表
    public static final class ROLE_TABLE {
        public static final int SUPER_ADMIN = 1; // 超级管理员
        public static final int SCHOOL_ADMIN = 2; // 学校管理员
        public static final int ACADEMIC_AFFAIRS_ADMIN = 3; // 教务处管理员
        public static final int DEPARTMENT_ADMIN = 4; // 院系管理员
        public static final int COMPANY_ADMIN = 5; // 企业管理员
        public static final int COMPANY_TUTOR = 6; // 企业导师
        public static final int SCHOOL_TEACHER = 7; // 学校教师
        public static final int STUDENT = 8; // 学生
    }

    // 用户身份类型编码（与前端 USER_JOB_CODE 保持一致）
    public static final class USER_JOB_CODE {
        public static final String SUPER_ADMIN = "SUPER_ADMIN";
        public static final String STUDENT = "STUDENT";
        public static final String COMPANY_TUTOR = "COMPANY_TUTOR";
        public static final String COMPANY_ADMIN = "COMPANY_ADMIN";
        public static final String SCHOOL_TEACHER = "SCHOOL_TEACHER";
        public static final String SCHOOL_ADMIN = "SCHOOL_ADMIN";
        public static final String DEPARTMENT_ADMIN = "DEPARTMENT_ADMIN";
        public static final String ACADEMIC_AFFAIRS_ADMIN = "ACADEMIC_AFFAIRS_ADMIN";
    }

    public static final class INTERNSHIP_RELATION_STATUS {
        public static final int ACTIVE = 0;
        public static final int TERMINATING = 1;
        public static final int TERMINATED = 2;
    }

    public static final class INTERNSHIP_TERMINATION_STATUS {
        public static final int PENDING = 0;
        public static final int APPROVED = 1;
        public static final int REJECTED = 2;
        public static final int RETURNED = 3;
        public static final int CANCELLED = 4;
    }

    public static final class AUDIT_STATUS {
        public static final int SAVE = -1; public static final String SAVENAME = "保存未提交";
        public static final int SUBMIT = 0; public static final String SUBMITNAME = "提交待审核";
        public static final int PASS = 1; public static final String PASSNAME = "审核通过";
        public static final int NOTPASS = 2; public static final String NOTPASSNAME = "审核未通过";
        public static final int BACK = 3; public static final String BACKNAME = "审核退回";
    }

    /**
     * 实习项目下师生关联的导师类型（保留供配置或其它模块引用）。
     */
    public static final class TUTOR_ASSIGN_KIND {
        /** 校内导师：待提交（SAVE）阶段可按导师池负载均衡写入或重算 teacherId；已提交后不再由自动分配接口改写 */
        public static final int INTERNAL = 1;
        /** 企业导师：仅创建 RelTeacherStudent 与审核记录，teacherId 留空，由后续接口手动指定 */
        public static final int ENTERPRISE = 2;
    }

    // 实习流程类型
    public static final class PROCESS_TYPE {
        public static final String INTERNSHIP_PLAN_MAKE = "INTERNSHIP_PLAN_MAKE"; // 实习计划制定
        public static final String STUDENT_SELECT_INTERNSHIP = "STUDENT_SELECT_INTERNSHIP"; // 学生实习项目安排
        public static final String TEACHER_SELECT_INTERNALSHIP = "TEACHER_SELECT_INTERNALSHIP"; // 指导老师实习项目安排
        public static final String INTERNAL_TEACHER_SELECT_PROJECT = "INTERNAL_TEACHER_SELECT_PROJECT"; // 校内实习-分配指导老师
        public static final String INTERNAL_TEACHER_DECLARE_TOPIC = "INTERNAL_TEACHER_DECLARE_TOPIC"; // 校内实习-老师申报题目
        public static final String INTERNAL_STUDENT_TEACHER_MATCH = "INTERNAL_STUDENT_TEACHER_MATCH"; // 校内实习-师生互选
        public static final String EXTERNAL_ENTERPRISE_POST_DECLARATION = "EXTERNAL_ENTERPRISE_POST_DECLARATION"; // 校外实习-企业岗位申报
        public static final String EXTERNAL_STUDENT_SELECT_POST = "EXTERNAL_STUDENT_SELECT_POST"; // 校外实习-学生报名岗位
        public static final String EXTERNAL_STUDENT_SELF_DECLARATION = "EXTERNAL_STUDENT_SELF_DECLARATION"; // 校外实习-学生自主申报
        public static final String EXTERNAL_STUDENT_ASSIGN_POST = "EXTERNAL_STUDENT_ASSIGN_POST"; // 校外实习-学生岗位安排
        public static final String EXTERNAL_ASSIGN_INTERNAL_TUTOR = "EXTERNAL_ASSIGN_INTERNAL_TUTOR"; // 校外实习-分配校内导师
        public static final String EXTERNAL_ENTERPRISE_ASSIGN_TUTOR = "EXTERNAL_ENTERPRISE_ASSIGN_TUTOR"; // 校外实习-分配企业导师
        /** 学生实习打卡审核（ViewRelProcessInternship.processTypeCode） */
        public static final String STUDENT_SIGN = "STUDENT_SIGN";
        public static final String STUDENT_INTERNSHIP_TERMINATION = "STUDENT_INTERNSHIP_TERMINATION";
    }

    public static final String BUCKET_NAME = "internship";

    // 审核级别
    public static final class VERIFY_LEVEL {
        public static final int NO_VERIFY = 1; // 无需审核
        public static final int ONE_VERIFY = 2; // 一级审核
        public static final int TWO_VERIFYS = 3; // 二级审核
        public static final int THREE_VERIFYS = 4; // 三级审核
        public static final int FOUR_VERIFYS = 5; // 四级审核
        public static final int FIVE_VERIFYS = 6; // 五级审核
    }

    


}
