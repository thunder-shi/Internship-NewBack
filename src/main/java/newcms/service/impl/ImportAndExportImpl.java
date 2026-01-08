package newcms.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.poi.excel.ExcelUtil;
import cn.hutool.poi.excel.ExcelWriter;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import newcms.base.Base;
import newcms.entity.base.BaseTreeInfo;
import newcms.entity.db.BaseDepartment;
import newcms.entity.db.BaseJobPosition;
import newcms.entity.db.BaseMajor;
import newcms.repository.db.BaseDepartmentDao;
import newcms.repository.db.BaseJobPositionDao;
import newcms.repository.db.BaseMajorDao;
import newcms.service.ICommonService;
import newcms.service.IDataListService;
import newcms.service.IDataTreeService;
import newcms.service.IImportAndExportService;
import newcms.utils.FastJsonUtil;
import newcms.utils.LogUtil;
import org.apache.poi.hssf.usermodel.*;
import org.apache.poi.hssf.util.HSSFColor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
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
            if (!StringUtils.isEmpty(row.getCell(i))){
                row.getCell(i).setCellType(CellType.STRING);
            }
            if ("".equals(row.getCell(i) == null ? "" : row.getCell(i).getStringCellValue().trim())){
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
        }catch (Exception ex) {
            LogUtil.error(logger, ex);
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
            Object resAll = ((Page<Object>)iCommonService.getSomeRecords(keyWords, searchKey, regKey, Sort.by(Sort.Direction.DESC, "id"),null,null,false, andor)).getContent();
            nodes = JSONObject.parseArray(FastJsonUtil.toJSONString(resAll));
            //判断下是否为树形结构
            if (nodes.size()>0) {
                Set<String> keys = nodes.getJSONObject(0).keySet();
                if (keys.contains("parentId")) {
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
    public void setLevelInfo(HSSFRow row , BaseTreeInfo curTreeInfo, List<BaseTreeInfo> allTreeInfo){
        for(int i = curTreeInfo.getTheLevel(); i > 0; i--){
            row.createCell(i-1).setCellValue(curTreeInfo.getName());
            for(BaseTreeInfo baseTreeInfo : allTreeInfo){
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
        Workbook workbook = null;
        try {
            if (file.getPath().endsWith("xls")) {
                logger.debug("读取 Excel 2003 版本文件");
                workbook = new HSSFWorkbook(new FileInputStream(file));
            } else if (file.getPath().endsWith("xlsx")) {
                workbook = new XSSFWorkbook(new FileInputStream(file));
                logger.debug("读取 Excel 2007 版本文件");
            }
        } catch (IOException e) {
            logger.error("读取 Excel 文件异常, file={}", file.getPath(), e);
        }

        return workbook;
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
        //写入浏览器
        try {
            String header  ="attachment;filename=" + URLEncoder.encode(name, StandardCharsets.UTF_8.toString()) + ".xls";
            response.setHeader("content-disposition", header);
            OutputStream outputStream = response.getOutputStream();
            workbook.write(outputStream);
            outputStream.flush();
            outputStream.close();
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
        List<Object> row1 = new ArrayList<>();
        List<Object> row2 = new ArrayList<>();
        List<Object> row3 = new ArrayList<>();
        switch (keyWords) {
            case "BaseMajor":
                //表头
//                row1 = CollUtil.newArrayList("填写示例：**********");
                row2 = CollUtil.newArrayList("专业代码", "专业名称", "备注");
                row3 = CollUtil.newArrayList("", "", "");
                rowsData = CollUtil.newArrayList(row2, row3);
                dataRow = CollUtil.newArrayList("", "", "");
                break;
            case "BaseUser":
                //表头
                row2 = CollUtil.newArrayList("姓名*", "性别", "联系电话", "邮箱", "账号*", "密码", "身份证号", "出生日期", "地址", "邮政编码", "昵称", "学院名称*", "班级名称*", "身份类别*", "工号", "专业名称*", "入学年份", "毕业年份");
                row3 = CollUtil.newArrayList("", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "");
                rowsData = CollUtil.newArrayList(row2, row3);
                dataRow = CollUtil.newArrayList("", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "");
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
        //设置样式
        CellStyle cellStyle1 = createCellStyleAndCellFont(writer, true, Font.COLOR_RED, (short) 11, HorizontalAlignment.LEFT);
        CellStyle cellStyle2 = createCellStyleAndCellFont(writer, true, Font.COLOR_NORMAL, (short) 11, HorizontalAlignment.LEFT);
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
    @Resource
    BaseMajorDao baseMajorDao;
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
        try (FileInputStream fileInputStream = new FileInputStream(file)) {
            //通过流的方式读取文件，支持xls和xlsx
            Workbook workbook = judgeVersion(file);
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
                    BaseMajor major = (BaseMajor)iCommonService.getOneRecordByCode("BaseMajor", parentCode, false);
                    parentId = major.getId();
                    if (parentId == null) {
                        parentId = -1;
                    }
                }
                JSONObject data = new JSONObject();
                data.put("code", code);
                data.put("name", name);
                data.put("remarks", remarks);
                data.put("parentId", parentId);
                result = iDataTreeService.editOneNode("BaseMajor", data);

            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return result;
    }


    @Override
    public Object importBaseUser(File file) {
        Object result = new Object();
        try (FileInputStream fileInputStream = new FileInputStream(file)) {
            //通过流的方式读取文件，支持xls和xlsx
            Workbook workbook = judgeVersion(file);
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
            for (int i = firstRowNum; i <= lastRowNum; i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                
                // 根据模板字段顺序读取：姓名, 性别, 联系电话, 邮箱, 账号, 密码, 身份证号, 出生日期, 地址, 邮政编码, 昵称, 学院名称, 班级名称, 身份类别, 工号, 专业名称, 入学年份, 毕业年份
                String name = getCellStringValue(row.getCell(0));
                String sex = getCellStringValue(row.getCell(1));
                String phone = getCellStringValue(row.getCell(2));
                String email = getCellStringValue(row.getCell(3));
                String account = getCellStringValue(row.getCell(4));
                String password = getCellStringValue(row.getCell(5));
                String idCard = getCellStringValue(row.getCell(6));
                String birthStr = getCellStringValue(row.getCell(7));
                String address = getCellStringValue(row.getCell(8));
                String postalCode = getCellStringValue(row.getCell(9));
                String nickName = getCellStringValue(row.getCell(10));
                String collegeName = getCellStringValue(row.getCell(11));
                String className = getCellStringValue(row.getCell(12));
                String jobName = getCellStringValue(row.getCell(13));
                String workId = getCellStringValue(row.getCell(14));
                String majorName = getCellStringValue(row.getCell(15));
                // 入学年份和毕业年份（BaseUser实体中暂无这些字段，保留读取以保持与模板一致）
                @SuppressWarnings("unused")
                String startYearStr = getCellStringValue(row.getCell(16));
                @SuppressWarnings("unused")
                String endYearStr = getCellStringValue(row.getCell(17));
                
                // 跳过空行（姓名为空则跳过）
                if (name.isEmpty()) continue;
                
                // 验证必填字段：departmentId、majorId、jobId 不能为空
                // 检查学院名称是否为空
                if (collegeName.isEmpty()) continue;
                // 检查班级名称是否为空
                if (className.isEmpty()) continue;
                // 检查身份类别是否为空
                if (jobName.isEmpty()) continue;
                // 检查专业名称是否为空
                if (majorName.isEmpty()) continue;
                
                JSONObject data = new JSONObject();
                data.put("name", name);
                if (!sex.isEmpty()) data.put("sex", sex);
                if (!phone.isEmpty()) data.put("phone", phone);
                if (!email.isEmpty()) data.put("email", email);
                if (!account.isEmpty()) data.put("account", account);
                // 如果密码为空，默认设为000000
                data.put("password", password.isEmpty() ? "000000" : password);
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
                
                // 通过学院名称和班级名称查找部门ID（必填）
                // 1. 通过学院名称查找学院实体（唯一）
                // 2. 通过班级名称查找班级实体集合（可能有重复）
                // 3. 在班级集合中找到parentId等于学院id的班级
                // 4. 使用该班级的id作为departmentId
                Integer departmentId = null;
                try {
                    // 查找学院实体（应该唯一）
                    List<BaseDepartment> collegeList = baseDepartmentDao.findByNameAndIsDeletedFalse(collegeName);
                    if (collegeList == null || collegeList.isEmpty()) {
                        logger.warn("未找到学院名称为 {} 的记录，跳过该行数据", collegeName);
                        continue;
                    }
                    if (collegeList.size() > 1) {
                        logger.warn("学院名称 {} 存在多条记录，跳过该行数据", collegeName);
                        continue;
                    }
                    BaseDepartment college = collegeList.get(0);
                    Integer collegeId = college.getId();
                    
                    // 查找班级实体集合（可能有重复）
                    List<BaseDepartment> classList = baseDepartmentDao.findByNameAndIsDeletedFalse(className);
                    if (classList == null || classList.isEmpty()) {
                        logger.warn("未找到班级名称为 {} 的记录，跳过该行数据", className);
                        continue;
                    }
                    
                    // 在班级集合中找到parentId等于学院id的班级
                    BaseDepartment targetClass = classList.stream()
                            .filter(clazz -> collegeId.equals(clazz.getParentId()))
                            .findFirst()
                            .orElse(null);
                    
                    if (targetClass == null) {
                        logger.warn("未找到班级名称为 {} 且父级为学院 {} 的记录，跳过该行数据", className, collegeName);
                        continue;
                    }
                    
                    departmentId = targetClass.getId();
                } catch (Exception e) {
                    logger.error("查找学院 {} 或班级 {} 时发生异常，跳过该行数据", collegeName, className);
                    continue;
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
                    logger.error("查找身份类别 {} 时发生异常，跳过该行数据", jobName);
                    continue;
                }
                if (jobId == null) {
                    logger.warn("身份类别 {} 对应的ID为空，跳过该行数据", jobName);
                    continue;
                }
                data.put("jobId", jobId);
                
                // 通过名称查找专业ID（必填）
                // 如果专业名称有重复，选择专业编码最长的那个
                Integer majorId = null;
                try {
                    List<BaseMajor> majorList = baseMajorDao.findByNameAndIsDeletedFalse(majorName);
                    if (majorList != null && !majorList.isEmpty()) {
                        // 如果有多条记录，按专业编码长度排序，选择编码最长的
                        BaseMajor majorObj = majorList.stream()
                                .max(Comparator.comparing(m -> m.getCode() != null ? m.getCode().length() : 0))
                                .orElse(null);
                        if (majorObj != null) {
                            majorId = majorObj.getId();
                        }
                    } else {
                        logger.warn("未找到专业名称为 {} 的记录，跳过该行数据", majorName);
                        continue;
                    }
                } catch (Exception e) {
                    logger.error("查找专业名称 {} 时发生异常，跳过该行数据", majorName);
                    continue;
                }
                if (majorId == null) {
                    logger.warn("专业名称 {} 对应的ID为空，跳过该行数据", majorName);
                    continue;
                }
                data.put("majorId", majorId);
                
                // 处理入学年份和毕业年份（BaseUser实体中没有这些字段，可能需要根据实际需求处理）
                // 如果BaseUser实体中有这些字段，可以添加：
                // if (!startYearStr.isEmpty()) {
                //     try {
                //         data.put("startYear", Integer.parseInt(startYearStr));
                //     } catch (NumberFormatException e) {
                //         // 解析失败，跳过
                //     }
                // }
                // if (!endYearStr.isEmpty()) {
                //     try {
                //         data.put("endYear", Integer.parseInt(endYearStr));
                //     } catch (NumberFormatException e) {
                //         // 解析失败，跳过
                //     }
                // }
                
                // BaseUser不是树形结构，使用iDataListService
                result = iDataListService.editOneNode("BaseUser", data);
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return result;
    }
}





