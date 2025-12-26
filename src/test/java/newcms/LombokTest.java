package newcms;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LombokTest {
    private String name;
    private Integer age;
    private Boolean isActive;
    
    public static void main(String[] args) {
        LombokTest test = new LombokTest();
        // 如果 Lombok 正常工作，这些方法应该可以调用
        test.setName("Test");
        test.setAge(25);
        test.setIsActive(true);
        
        System.out.println("Name: " + test.getName());
        System.out.println("Age: " + test.getAge());
        System.out.println("IsActive: " + test.getIsActive());
        
        System.out.println("Lombok 工作正常！");
    }
}

