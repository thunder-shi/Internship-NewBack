package newcms.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.poi.excel.ExcelUtil;
import cn.hutool.poi.excel.ExcelWriter;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import newcms.base.Base;
import newcms.base.BaseException;
import newcms.base.BaseResponse;
import newcms.entity.base.BaseTreeInfo;
import newcms.entity.db.BaseDepartment;
import newcms.entity.db.BaseJobPosition;
import newcms.entity.db.BaseMajor;
import newcms.entity.db.SysRole;
import newcms.repository.db.BaseDepartmentDao;
import newcms.repository.db.BaseJobPositionDao;
import newcms.repository.db.SysRoleDao;
import newcms.service.ICommonService;
import newcms.service.IDataListService;
import newcms.service.IDataTreeService;
import newcms.service.IImportAndExportService;
import newcms.service.IUserService;
import newcms.utils.EncodeUtil;
import newcms.utils.FastJsonUtil;
import newcms.utils.LogUtil;
import org.apache.poi.hssf.usermodel.*;
import org.apache.poi.hssf.util.HSSFColor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
@Transactional(rollbackFor = Exception.class)
public class ImportAndExportImpl extends Base implements IImportAndExportService {
    @Resource
    private ApplicationContext applicationContext;
    @Resource
    private IDataTreeService iDataTreeService;
    @Resource
    private ICommonService iCommonService;
    @Resource
    private IDataListService iDataListService;
    @Resource
    private IUserService iUserService;
    @Resource
    private SysRoleDao sysRoleDao;

    //region 一些private
    private HSSFCellStyle getCellStype( HSSFWorkbook workbook, int type) {
        HSSFCellStyle cellStyle = workbook.createCellStyle();
        HSSFFont hssfFont = workbook.createFont();
        switch (type) {
            case 1: //表头
                hssfFont.setBold(true);
                hssfFont.setColor(HSSFColor.HSSFColorPredefined.SEA_GREEN.getIndex());
                cellStyle.setFont(hssfFont);
                cellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                cellStyle.setFillForegroundColor(HSSFColor.HSSFColorPredefined.LIGHT_CORNFLOWER_BLUE.getIndex());
                cellStyle.setAlignment(HorizontalAlignment.CENTER);
                cellStyle.setVerticalAlignment(VerticalAlignment.CENTER);
                cellStyle.setBorderBottom(BorderStyle.THIN);
                cellStyle.setBorderTop(BorderStyle.THIN);
                cellStyle.setBorderLeft(BorderStyle.THIN);
                cellStyle.setBorderRight(BorderStyle.THIN);
                break;
            case 2:
                hssfFont.setBold(true);
                hssfFont.setFontHeight((short)300);
                cellStyle.setFont(hssfFont);

                cellStyle.setAlignment(HorizontalAlignment.CENTER);
                cellStyle.setVerticalAlignment(VerticalAlignment.CENTER);
                break;
            case 3:
                cellStyle.setVerticalAlignment(VerticalAlignment.CENTER);
                break;
            default:
                cellStyle.setVerticalAlignment(VerticalAlignment.CENTER);
                cellStyle.setBorderBottom(BorderStyle.THIN);
                cellStyle.setBorderTop(BorderStyle.THIN);
                cellStyle.setBorderLeft(BorderStyle.THIN);
                cellStyle.setBorderRight(BorderStyle.THIN);
                break;
        }
        return cellStyle;
    }

    /**
     * 创建sheet并创建表头
     */
    private HSSFSheet createSheetandHeader(String key, HSSFWorkbook workbook, List<String> headerTitles, JSONArray nodes) {
        //创建一个sheet
        HSSFSheet hssfSheet = workbook.createSheet();
        //创建样式
        HSSFCellStyle titleStyle = getCellStype(workbook,1);
        //创建表头
        int rowNum = 0;
        if (key.equals("校级报名表")) {
            int lastCol = 3;
            CellRangeAddress rangeAddress = new CellRangeAddress(0, 0, 0, lastCol);
            //添加要合并地址到表格
            hssfSheet.addMergedRegion(rangeAddress);
            HSSFRow row1 = hssfSheet.createRow(rowNum);
            HSSFCell cell1 = row1.createCell(0);
            cell1.setCellValue(nodes.getJSONObject(0).getString("contestName")+"报名表");
            cell1.setCellStyle(getCellStype(workbook, 2));
            rowNum++;

            rangeAddress = new CellRangeAddress(1, 1, 0, lastCol);
            hssfSheet.addMergedRegion(rangeAddress);
            HSSFRow row2 = hssfSheet.createRow(rowNum);
            HSSFCell cell2 = row2.createCell(0);
            cell2.setCellValue("学校名称：" + nodes.getJSONObject(0).getString("schoolName"));
            cell2.setCellStyle(getCellStype(workbook, 3));
            rowNum++;

            HSSFRow row0 = hssfSheet.createRow(rowNum);
            int j = 0;
            HSSFCell cell01 = row0.createCell(j);
            cell01.setCellValue("组别/项目");
            cell01.setCellStyle(titleStyle);
            j++;
            HSSFCell cell02 = row0.createCell(j);
            cell02.setCellValue("队伍编码");
            cell02.setCellStyle(titleStyle);
            j++;
            HSSFCell cell03 = row0.createCell(j);
            cell03.setCellValue("教练");
            cell03.setCellStyle(titleStyle);
            j++;
            HSSFCell cell04 = row0.createCell(j);
            cell04.setCellValue("队员");
            cell04.setCellStyle(titleStyle);
        } else {
            HSSFRow row0 = hssfSheet.createRow(rowNum);
            for (int i = 0; i < headerTitles.size(); i++) {
                HSSFCell cell0 = row0.createCell(i);
                cell0.setCellValue(headerTitles.get(i));
                cell0.setCellStyle(titleStyle);
            }
        }
        return hssfSheet;
    }
    /**
     * excel表格根据内容自适应列宽
     * @author yukai
     * @param sheet
     * @param columnLength 列数
     */
    private void setSizeColumn(HSSFSheet sheet, int columnLength) {
        for (int columnNum = 0; columnNum <= columnLength; columnNum++) {
            int columnWidth = sheet.getColumnWidth(columnNum) / 256;
            for (int rowNum = 0; rowNum <= sheet.getLastRowNum(); rowNum++) {
                // 当前行未被使用过
                HSSFRow currentRow;
                if (sheet.getRow(rowNum) == null) {
                    currentRow = sheet.createRow(rowNum);
                } else {
                    currentRow = sheet.getRow(rowNum);
                }
                if (currentRow.getCell(columnNum) != null) {
                    HSSFCell currentCell = currentRow.getCell(columnNum);
                    if (currentCell.getCellType() == CellType.STRING) {
                        //兼容中文
                        int length = (currentCell.getStringCellValue().getBytes().length + currentCell.getStringCellValue().length()) / 2;
                        if (columnWidth < length) {
                            columnWidth = length;
                        }
                    }
                }
            }
            sheet.setColumnWidth(columnNum, columnWidth * 256);
        }
    }

    /**
     * 获取值之前判断是否有值
     * @author yukai
     * @param row
     * @param index
     */
    public boolean checkRow(Row row, int index){
        int num = 0;
        for (int i = 0;i < index; i++) {
            Cell cell = row.getCell(i);
            if (cell != null){
                // 使用 DataFormatter 安全地获取字符串值，避免使用已弃用的 setCellType
                DataFormatter formatter = new DataFormatter();
                String cellValue = formatter.formatCellValue(cell).trim();
                if ("".equals(cellValue)){
                    num++;
                }
            } else {
                num++;
            }
        }
        return num == index;
    }

    private void dealExportInfo(String key, HSSFWorkbook wb, List<String> headerTitles, List<String> headerColNames, JSONArray nodes) {
        //设置表头
        HSSFSheet sheet = createSheetandHeader(key, wb, headerTitles, nodes);
        HSSFRow row;
        try {
            if (nodes.size() > 0) {
                List<Object> keys = new ArrayList<>();
                int j = 0;
                int span = 1;
                if (key.equals("校级报名表")) {
                    span = 3;
                    keys.add("smallContestTypeName");
                    keys.add("code");
                    keys.add("allTeacherNames");
                    keys.add("allStudentNames");
                } else {
                    // keys = Arrays.asList(nodes.getJSONObject(0).keySet().toArray());
                    keys = Arrays.asList(headerColNames.toArray());
                }
                HSSFCellStyle contentStyle = getCellStype(wb, 0);
                for (int i = 0; i < nodes.size(); i++) {
                    j = 0;
                    for (String colName : headerColNames) {
                        if (j == 0) {
                            row = sheet.createRow(i + span);
                        } else {
                            row = sheet.getRow(i + span);
                        }
                        String ss = "";
                        if (keys.contains(colName)) {
                            if (colName.contains("Date") || colName.contains("Time")) {
                                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                                ss = format.format(nodes.getJSONObject(i).getDate(colName));
                            } else if (colName.contains("isAudit")) {
                                switch (nodes.getJSONObject(i).getInteger(colName).intValue()) {
                                    case AUDIT_STATUS.SAVE:
                                        ss = AUDIT_STATUS.SAVENAME;
                                        break;
                                    case AUDIT_STATUS.SUBMIT:
                                        ss = AUDIT_STATUS.SUBMITNAME;
                                        break;
                                    case AUDIT_STATUS.PASS:
                                        ss = AUDIT_STATUS.PASSNAME;
                                        break;
                                    case AUDIT_STATUS.NOTPASS:
                                        ss = AUDIT_STATUS.NOTPASSNAME;
                                        break;
                                    case AUDIT_STATUS.BACK:
                                        ss = AUDIT_STATUS.BACKNAME;
                                        break;
                                }
                            } else {
                                ss = nodes.getJSONObject(i).getString(colName);
                            }
                            if (ss != null) {
                                row.createCell(j).setCellValue(ss);
                                row.getCell(j).setCellStyle(contentStyle);
                            } else {
                                row.createCell(j).setCellValue("无");
                                row.getCell(j).setCellStyle(contentStyle);
                            }
                        } else {
                            row.createCell(j).setCellValue("无");
                            row.getCell(j).setCellStyle(contentStyle);
                        }
                        j++;
                    }
                }
            }
        } catch (Exception ex) {
            // EXC-06: 不再静默吞掉，让调用方感知并处理（避免返回不完整文件）
            LogUtil.error(logger, ex);
            throw new RuntimeException("导出数据时发生错误", ex);
        }
        setSizeColumn(sheet, headerColNames.size());
    }
    //endregion



    @Override
    public Object exportInfo(String key, JSONArray nodes, JSONArray allTableColumns, JSONObject searchWords) {
        String keyWords = "";
        switch (key) {
            case "校级报名表": keyWords="ViewRegisterGroupDetail"; break;
            default: keyWords=key; break;
        }

        HSSFWorkbook wb = new HSSFWorkbook();
        JSONObject searchViewTableKey = new JSONObject();
        searchViewTableKey.put("tableName", keyWords);
        List<String> headerTitles = new ArrayList<>();
        List<String> headerColNames = new ArrayList<>();
        wb.createName().setNameName("导出数据");
        if (allTableColumns.size() != 0) {
            for (int i=0;i<allTableColumns.size();i++) {
                JSONObject obj = allTableColumns.getJSONObject(i);
                headerTitles.add(obj.get("showName").toString());
                headerColNames.add(obj.get("tableColumnName").toString());
            }
        } else {
            headerTitles.add("编码"); headerColNames.add("code");
            headerTitles.add("名称"); headerColNames.add("name");
            headerTitles.add("备注"); headerColNames.add("remarks");
        }
        //数据导出处理方法
        if (nodes.size()==0) {
            JSONObject searchKey = searchWords.getJSONObject("searchKey");
            JSONObject regKeyJson = searchWords.getJSONObject("regKey");
            JSONObject andorJson = searchWords.getJSONObject("andor");
            // 转换JSONObject为Map类型
            Map<String, String> regKey = regKeyJson != null ? regKeyJson.toJavaObject(new com.alibaba.fastjson.TypeReference<Map<String, String>>(){}) : null;
            Map<String, Boolean> andor = andorJson != null ? andorJson.toJavaObject(new com.alibaba.fastjson.TypeReference<Map<String, Boolean>>(){}) : null;
            @SuppressWarnings("unchecked")
            Page<Object> pageResult = (Page<Object>) iCommonService.getSomeRecords(keyWords, searchKey, regKey, Sort.by(Sort.Direction.DESC, "id"),null,null,false, andor);
            Object resAll = pageResult.getContent();
            nodes = JSONObject.parseArray(FastJsonUtil.toJSONString(resAll));
            //判断下是否为树形结构
            if (nodes.size()>0) {
                Set<String> keys = nodes.getJSONObject(0).keySet();
                if (keys.contains("parentId")) {
                    @SuppressWarnings("unchecked")
                    ArrayList<Object> trees = (ArrayList<Object>) iDataTreeService.getTreeArrayList(keyWords, searchKey);
                    nodes.clear();
                    nodes = JSONObject.parseArray(FastJsonUtil.toJSONString(trees));
                }
            }
        }
        dealExportInfo(key, wb, headerTitles, headerColNames, nodes);
        return writeBrowser(wb, "ExportedData");
    }


    /**
     * 处理字段：数据库中为String，但是实际上为全数字，excel中默认的为数值型。进行转为String
     * @param row
     * @param i
     * @return String
     */
    public String dealWithStringRow(Row row , Integer i){
        String res = "";
        if (i>=0) {
            if (row.getCell(i) == null) {
                res = "";
            } else {
                if (row.getCell(i).getCellType() == CellType.NUMERIC) {
                    if (String.valueOf(row.getCell(i).getNumericCellValue()).indexOf("E") == -1) {
                        res = String.valueOf(row.getCell(i).getNumericCellValue());
                    } else {
                        res = new DecimalFormat("#").format(row.getCell(i).getNumericCellValue());
                    }
                } else if (row.getCell(i).getCellType() == CellType.STRING) {
                    res = row.getCell(i).getStringCellValue();
                } else if (row.getCell(i).getCellType() == CellType.BLANK) {
                    res = "";
                }
            }
        }
        return res;
    }

    /**
     * 写入树结构层级信息
     * @param row
     * @param curTreeInfo
     * @param allTreeInfo
     */
    public void setLevelInfo(HSSFRow row , BaseTreeInfo<?> curTreeInfo, List<BaseTreeInfo<?>> allTreeInfo){
        for(int i = curTreeInfo.getTheLevel(); i > 0; i--){
            row.createCell(i-1).setCellValue(curTreeInfo.getName());
            for(BaseTreeInfo<?> baseTreeInfo : allTreeInfo){
                if(baseTreeInfo.getId().equals(curTreeInfo.getParentId())){
                    curTreeInfo = baseTreeInfo;
                    break;
                }
            }
        }
    }
    /**
     * 树转变为列表
     * @author yukai
     * @param treeInfo
     * @param list
     */
    public void treeToList(JSONObject treeInfo, List<JSONObject> list){
        list.add(treeInfo);
        if(treeInfo.getJSONArray("childList") != null) {
            for (JSONObject childTreeInfo : FastJsonUtil.toArray(treeInfo.getJSONArray("childList"), JSONObject.class)){
                treeToList(childTreeInfo, list);
            }
        }
    }

    /**
     * 判断excel文件版本
     * @author yukai
     * @param file
     */
    public Workbook judgeVersion(File file) {
        // EXC-02: 使用 try-with-resources 确保 FileInputStream 在异常时正确关闭
        try {
            if (file.getPath().endsWith("xls")) {
                logger.debug("读取 Excel 2003 版本文件");
                try (FileInputStream fis = new FileInputStream(file)) {
                    return new HSSFWorkbook(fis);
                }
            } else if (file.getPath().endsWith("xlsx")) {
                logger.debug("读取 Excel 2007 版本文件");
                try (FileInputStream fis = new FileInputStream(file)) {
                    return new XSSFWorkbook(fis);
                }
            }
        } catch (IOException e) {
            logger.error("读取 Excel 文件异常, file={}", file.getPath(), e);
        }
        return null;
    }

    /**
     * 文件写入浏览器
     * @author yukai
     * @param workbook
     * @param name
     */
    public Boolean writeBrowser(HSSFWorkbook workbook, String name) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletResponse response = attributes.getResponse();
        // EXC-03: 使用 try-with-resources 确保 OutputStream 在写入异常时也能关闭
        try {
            String header = "attachment;filename=" + URLEncoder.encode(name, StandardCharsets.UTF_8.toString()) + ".xls";
            response.setHeader("content-disposition", header);
            try (OutputStream outputStream = response.getOutputStream()) {
                workbook.write(outputStream);
                outputStream.flush();
            }
            return true;
        } catch (Exception e) {
            logger.error("写入浏览器异常, name={}", name, e);
            return false;
        }
    }

    /**
     * 文件写入浏览器 (Hutool ExcelWriter版本)
     * @param writer ExcelWriter对象
     * @param name 文件名
     */
    public Boolean writeBrowser(ExcelWriter writer, String name) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletResponse response = attributes.getResponse();
        try {
            String header = "attachment;filename=" + URLEncoder.encode(name, StandardCharsets.UTF_8.toString()) + ".xlsx";
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;charset=utf-8");
            response.setHeader("content-disposition", header);
            OutputStream outputStream = response.getOutputStream();
            writer.flush(outputStream, true);
            writer.close();
            return true;
        } catch (Exception e) {
            logger.error("写入浏览器异常, name={}", name, e);
            return false;
        }
    }

    /**
     * 下载模板
     * @param keyWords
     */
    @Override
    public void downTemplate(String keyWords) {
        List<List<Object>> rowsData = new ArrayList<>();
        List<Object> dataRow = new ArrayList<>();
        List<Object> row2 = new ArrayList<>();
        List<Object> row3 = new ArrayList<>();
        switch (keyWords) {
            case "BaseMajor":
                //表头
                row2 = CollUtil.newArrayList("专业代码", "专业名称", "备注");
                row3 = CollUtil.newArrayList("", "", "");
                rowsData = CollUtil.newArrayList(row2, row3);
                dataRow = CollUtil.newArrayList("", "", "");
                break;
            case "BaseUser":
                //表头
                row2 = CollUtil.newArrayList("姓名*", "性别", "联系电话", "邮箱", "账号*", "密码", "身份证号", "出生日期", "地址", "邮政编码", "昵称", "部门编码*", "身份类别*", "工号", "专业代码*", "入学年份", "毕业年份", "学制（年）", "角色*");
                row3 = CollUtil.newArrayList("", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "");
                rowsData = CollUtil.newArrayList(row2, row3);
                dataRow = CollUtil.newArrayList("", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "");
                break;
        }
        //给除表头外 50行单元格非必填项设置默认值（空字符串）
        for (int i = 0; i < 50; i++) {
            rowsData.add(dataRow);
        }
        // 一次性写出内容，并设置样式，强制输出标题
        ExcelWriter writer = ExcelUtil.getWriter();
        //往sheet0中写入数据
        writer.write(rowsData, false);
        //设置样式（当前未使用，保留以备将来扩展）
        @SuppressWarnings("unused")
        CellStyle cellStyle1 = createCellStyleAndCellFont(writer, true, Font.COLOR_RED, (short) 11, HorizontalAlignment.LEFT);
        @SuppressWarnings("unused")
        CellStyle cellStyle2 = createCellStyleAndCellFont(writer, true, Font.COLOR_NORMAL, (short) 11, HorizontalAlignment.LEFT);
        @SuppressWarnings("unused")
        CellStyle cellStyle3 = createCellStyleAndCellFont(writer, false, Font.COLOR_NORMAL, (short) 11, HorizontalAlignment.LEFT);
        writeBrowser(writer, "test");
    }

    public static CellStyle createCellStyleAndCellFont(ExcelWriter writer, Boolean bold, short color, short fontHeightInPoints, HorizontalAlignment align) {
        CellStyle cellStyle = writer.createCellStyle();
        cellStyle.cloneStyleFrom(writer.getOrCreateRow(0).getCell(0).getCellStyle());
        Font font = writer.createFont();
        font.setBold(bold);
        font.setColor(color);
        //字体大小
        font.setFontHeightInPoints(fontHeightInPoints);
        cellStyle.setFont(font);
        //对齐方式
        cellStyle.setAlignment(align);
        //换行显示
        cellStyle.setWrapText(true);
        //
        //上下左右边框样式
        cellStyle.setBorderBottom(BorderStyle.NONE);
        cellStyle.setBorderLeft(BorderStyle.NONE);
        cellStyle.setBorderRight(BorderStyle.NONE);
        cellStyle.setBorderTop(BorderStyle.NONE);
        return cellStyle;
    }

    @Resource
    BaseDepartmentDao baseDepartmentDao;
    @Resource
    BaseJobPositionDao baseJobPositionDao;
    /**
     * 安全获取单元格字符串值，自动处理不同类型
     */
    private String getCellStringValue(Cell cell) {
        if (cell == null) {
            return "";
        }
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                // 判断是否为整数
                double numValue = cell.getNumericCellValue();
                if (numValue == Math.floor(numValue) && !Double.isInfinite(numValue)) {
                    return String.valueOf((long) numValue);
                }
                return String.valueOf(numValue);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (Exception e) {
                    return String.valueOf(cell.getNumericCellValue());
                }
            case BLANK:
            default:
                return "";
        }
    }

    @Override
    public Object importBaseMajor(File file) {
        Object result = new Object();
        //通过流的方式读取文件，支持xls和xlsx
        Workbook workbook = judgeVersion(file);
        try {
            if (workbook == null) {
                throw new RuntimeException("不支持的文件格式");
            }
            //通过sheet的名字来获取数据
            Sheet sheet = workbook.getSheetAt(0);
            //通过下标来获取数据
            //获取第一行的下标
            int firstRowNum = 1;
            //获取最后一行下标
            int lastRowNum = sheet.getLastRowNum();
            for (int i = firstRowNum; i <= lastRowNum; i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                String code = getCellStringValue(row.getCell(0));
                String name = getCellStringValue(row.getCell(1));
                String remarks = getCellStringValue(row.getCell(2));
                // 跳过空行
                if (code.isEmpty() && name.isEmpty()) continue;
                String parentCode = code.substring(0, code.length() - 2);
                Integer parentId = null;
                if (parentCode.isEmpty()) {
                    parentId = -1;
                }
                else {
                    // BUG-05: major 查不到时直接 .getId() → NPE；后面的 null 保护永远不执行
                    BaseMajor major = (BaseMajor)iCommonService.getOneRecordByCode("BaseMajor", parentCode, false);
                    if (major == null || major.getId() == null) {
                        parentId = -1;
                    } else {
                        parentId = major.getId();
                    }
                }
                JSONObject data = new JSONObject();
                data.put("code", code);
                data.put("name", name);
                data.put("remarks", remarks);
                data.put("parentId", parentId);
                result = iDataTreeService.editOneNode("BaseMajor", data);

            }

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("导入数据时发生错误", e);
        }
        return result;
    }


    @Override
    public Object importBaseUser(File file) {
        Object result = new Object();
        //通过流的方式读取文件，支持xls和xlsx
        Workbook workbook = judgeVersion(file);
        try {
            if (workbook == null) {
                throw new RuntimeException("不支持的文件格式");
            }
            //通过sheet的名字来获取数据
            Sheet sheet = workbook.getSheetAt(0);
            //通过下标来获取数据
            //获取第一行的下标（跳过表头）
            int firstRowNum = 1;
            //获取最后一行下标
            int lastRowNum = sheet.getLastRowNum();
            Set<String> importedAccounts = new HashSet<>();
            for (int i = firstRowNum; i <= lastRowNum; i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                
                // 根据模板字段顺序读取：姓名, 性别, 联系电话, 邮箱, 账号, 密码(已忽略), 身份证号, ...
                String name = getCellStringValue(row.getCell(0));
                String sex = getCellStringValue(row.getCell(1));
                String phone = getCellStringValue(row.getCell(2));
                String email = getCellStringValue(row.getCell(3));
                String account = getCellStringValue(row.getCell(4));
                String idCard = getCellStringValue(row.getCell(6));
                String birthStr = getCellStringValue(row.getCell(7));
                String address = getCellStringValue(row.getCell(8));
                String postalCode = getCellStringValue(row.getCell(9));
                String nickName = getCellStringValue(row.getCell(10));
                String departmentCode = getCellStringValue(row.getCell(11));
                String jobName = getCellStringValue(row.getCell(12));
                String workId = getCellStringValue(row.getCell(13));
                String majorCode = getCellStringValue(row.getCell(14));
                String startYearStr = getCellStringValue(row.getCell(15));
                String endYearStr = getCellStringValue(row.getCell(16));
                String schoolLengthStr = getCellStringValue(row.getCell(17));
                String rolesStr = getCellStringValue(row.getCell(18));
                
                // 跳过空行（姓名为空则跳过）
                if (name.isEmpty()) continue;
                
                // 验证必填字段：departmentId、majorId、jobId 不能为空
                // 检查部门编码是否为空
                if (departmentCode.isEmpty()) continue;
                // 检查身份类别是否为空
                if (jobName.isEmpty()) continue;
                // 检查专业代码是否为空
                if (majorCode.isEmpty()) continue;
                // 角色为必填
                if (rolesStr.isEmpty()) {
                    throw importRowError(i + 1, "角色不能为空");
                }
                if (account.isEmpty()) {
                    throw importRowError(i + 1, "账号不能为空");
                }
                String normalizedAccount = account.trim();
                if (importedAccounts.contains(normalizedAccount)) {
                    throw importRowError(i + 1, "账号 " + normalizedAccount + " 在导入文件中重复");
                }
                JSONObject accountSearch = new JSONObject();
                accountSearch.put("account", normalizedAccount);
                @SuppressWarnings("unchecked")
                Page<Object> existingUserPage = (Page<Object>) iCommonService.getSomeRecords("BaseUser", accountSearch);
                if (existingUserPage.getContent() != null && !existingUserPage.getContent().isEmpty()) {
                    throw importRowError(i + 1, "账号 " + normalizedAccount + " 已存在，不能重复导入");
                }
                importedAccounts.add(normalizedAccount);

                JSONObject data = new JSONObject();
                data.put("name", name);
                if (!sex.isEmpty()) data.put("sex", sex);
                if (!phone.isEmpty()) data.put("phone", phone);
                if (!email.isEmpty()) data.put("email", email);
                data.put("account", normalizedAccount);
                // 密码在获取 userId 后按姓名自动生成（同重置密码逻辑）
                if (!idCard.isEmpty()) data.put("idCard", idCard);
                // 处理出生日期
                if (!birthStr.isEmpty()) {
                    try {
                        data.put("birth", new SimpleDateFormat("yyyy-MM-dd").parse(birthStr));
                    } catch (Exception e) {
                        // 日期解析失败，跳过该字段
                    }
                }
                if (!address.isEmpty()) data.put("address", address);
                if (!postalCode.isEmpty()) data.put("postalCode", postalCode);
                if (!nickName.isEmpty()) data.put("nickName", nickName);
                if (!workId.isEmpty()) data.put("workId", workId);
                
                // 通过部门编码查找部门ID（必填）
                Integer departmentId = null;
                try {
                    BaseDepartment department = (BaseDepartment) iCommonService.getOneRecordByCode("BaseDepartment", departmentCode, false);
                    if (department == null) {
                        logger.warn("未找到部门编码为 {} 的记录，跳过该行数据", departmentCode);
                        continue;
                    }
                    departmentId = department.getId();
                } catch (Exception e) {
                    // DATA-04: 抛出而非 continue，确保事务回滚（全有或全无）
                    throw importRowError(i + 1, "查找部门编码 " + departmentCode + " 时发生异常", e);
                }
                if (departmentId == null) {
                    logger.warn("未找到匹配的部门ID，跳过该行数据");
                    continue;
                }
                data.put("departmentId", departmentId);
                
                // 通过名称查找身份类别ID（必填）
                Integer jobId = null;
                try {
                    BaseJobPosition jobPosition = baseJobPositionDao.findByNameAndIsDeletedFalse(jobName);
                    if (jobPosition != null) {
                        jobId = jobPosition.getId();
                    } else {
                        logger.warn("未找到身份类别为 {} 的记录，跳过该行数据", jobName);
                        continue;
                    }
                } catch (Exception e) {
                    throw importRowError(i + 1, "查找身份类别 " + jobName + " 时发生异常", e);
                }
                if (jobId == null) {
                    logger.warn("身份类别 {} 对应的ID为空，跳过该行数据", jobName);
                    continue;
                }
                data.put("jobId", jobId);
                
                // 通过专业代码查找专业ID（必填）
                Integer majorId = null;
                try {
                    BaseMajor major = (BaseMajor) iCommonService.getOneRecordByCode("BaseMajor", majorCode, false);
                    if (major == null) {
                        logger.warn("未找到专业代码为 {} 的记录，跳过该行数据", majorCode);
                        continue;
                    }
                    majorId = major.getId();
                } catch (Exception e) {
                    throw importRowError(i + 1, "查找专业代码 " + majorCode + " 时发生异常", e);
                }
                if (majorId == null) {
                    logger.warn("专业代码 {} 对应的ID为空，跳过该行数据", majorCode);
                    continue;
                }
                data.put("majorId", majorId);
                if (!startYearStr.isEmpty()) {
                    try {
                        data.put("startYear", Integer.parseInt(startYearStr.trim()));
                    } catch (NumberFormatException e) {
                        logger.warn("第 {} 行：入学年份无法解析为整数，已忽略: {}", i + 1, startYearStr);
                    }
                }
                if (!endYearStr.isEmpty()) {
                    try {
                        data.put("endYear", Integer.parseInt(endYearStr.trim()));
                    } catch (NumberFormatException e) {
                        logger.warn("第 {} 行：毕业年份无法解析为整数，已忽略: {}", i + 1, endYearStr);
                    }
                }
                if (!schoolLengthStr.isEmpty()) {
                    try {
                        data.put("schoolLength", Integer.parseInt(schoolLengthStr.trim()));
                    } catch (NumberFormatException e) {
                        logger.warn("第 {} 行：学制无法解析为整数，已忽略: {}", i + 1, schoolLengthStr);
                    }
                }

                // BaseUser不是树形结构，使用iDataListService
                Object editResult = iDataListService.editOneNode("BaseUser", data);
                
                // 从editOneNode返回的结果中提取新增的id
                JSONObject editResultJson = FastJsonUtil.toJson(editResult);
                Integer userId = null;
                // editOneNode返回的是保存后的实体对象，直接提取id
                if (editResultJson != null) {
                    userId = editResultJson.getInteger("id");
                }
                
                // 如果成功获取到userId，则加密密码并更新
                if (userId != null) {
                    try {
                        String rawPassword = EncodeUtil.buildResetPasswordFromName(name);
                        String encryptedPassword = EncodeUtil.pwdShiro(rawPassword, userId);
                        JSONObject updateData = new JSONObject();
                        updateData.put("id", userId);
                        updateData.put("password", encryptedPassword);
                        iDataListService.editOneNode("BaseUser", updateData);
                    } catch (IllegalArgumentException e) {
                        throw importRowError(i + 1, "生成初始密码失败，" + e.getMessage());
                    } catch (Exception e) {
                        throw importRowError(i + 1, "加密更新密码失败，userId: " + userId, e);
                    }
                }
                
                if (userId != null) {
                    try {
                        Integer[] roleIds = resolveImportUserRoleIds(rolesStr, i + 1);
                        iUserService.saveUserRoles(String.valueOf(userId), roleIds);
                    } catch (RuntimeException e) {
                        throw e;
                    } catch (Exception e) {
                        throw importRowError(i + 1, "设置用户角色失败，userId: " + userId, e);
                    }
                }
            }

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw BaseResponse.moreInfoError.error("导入数据时发生错误");
        }
        return result;
    }

    /**
     * 解析导入 Excel「角色*」列：多个角色名用英文或中文分号隔开（必填，至少一个有效角色名）。
     */
    private Integer[] resolveImportUserRoleIds(String rolesStr, int rowNum) {
        List<Integer> roleIds = new ArrayList<>();
        for (String part : rolesStr.split("[;；]")) {
            String roleName = part.trim();
            if (roleName.isEmpty()) {
                continue;
            }
            SysRole role = sysRoleDao.findByNameAndIsDeletedFalse(roleName);
            if (role == null || role.getId() == null) {
                throw importRowError(rowNum, "未找到角色「" + roleName + "」");
            }
            if (!roleIds.contains(role.getId())) {
                roleIds.add(role.getId());
            }
        }
        if (roleIds.isEmpty()) {
            throw importRowError(rowNum, "角色不能为空或无效");
        }
        return roleIds.toArray(new Integer[0]);
    }

    private static BaseException importRowError(int rowNum, String detail) {
        return BaseResponse.moreInfoError.error("第 " + rowNum + " 行：" + detail);
    }

    private static BaseException importRowError(int rowNum, String detail, Throwable cause) {
        BaseException ex = importRowError(rowNum, detail);
        ex.initCause(cause);
        return ex;
    }
}





