package com.easyink.common.utils.poi;

import com.easyink.common.annotation.Excel;
import com.easyink.common.annotation.Excel.ColumnType;
import com.easyink.common.annotation.Excel.Type;
import com.easyink.common.annotation.Excels;
import com.easyink.common.config.RuoYiConfig;
import com.easyink.common.constant.Constants;
import com.easyink.common.core.domain.AjaxResult;
import com.easyink.common.core.text.Convert;
import com.easyink.common.exception.CustomException;
import com.easyink.common.utils.DateUtils;
import com.easyink.common.utils.DictUtils;
import com.easyink.common.utils.StringUtils;
import com.easyink.common.utils.file.FileUtils;
import com.easyink.common.utils.reflect.ReflectUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.poi.hssf.usermodel.HSSFDateUtil;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFDataValidation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Excel相关处理
 *
 * @author admin
 */
public class ExcelUtil<T> {
    private static final Logger log = LoggerFactory.getLogger(ExcelUtil.class);

    /**
     * Excel sheet最大行数，默认65536
     */
    public static final int SHEET_SIZE = 65536;

    /**
     * 工作表名称
     */
    private String sheetName;

    /**
     * 导出类型（EXPORT:导出数据；IMPORT：导入模板）
     */
    private Type type;

    /**
     * 工作薄对象
     */
    private Workbook wb;

    /**
     * 工作表对象
     */
    private Sheet sheet;

    /**
     * 样式列表
     */
    private Map<String, CellStyle> styles;

    /**
     * 导入导出数据列表
     */
    private List<T> list;

    /**
     * 注解列表
     */
    private List<Object[]> fields;

    /**
     * 实体对象
     */
    private Class<T> clazz;
    /**
     * 是否是自定义选择导出字段
     */
    private Boolean isCustom;
    /**
     * 自定义选中导出的字段名集合
     */
    private List<String> selectedProperties = new ArrayList<>();


    public ExcelUtil(Class<T> clazz) {
        this.clazz = clazz;
    }

    public void init(List<T> list, String sheetName, Type type) {
        if (list == null) {
            list = new ArrayList<>();
        }
        this.list = list;
        this.sheetName = FileUtils.replaceFileNameUnValidChar(sheetName);
        this.type = type;
        createExcelField();
        createWorkbook();
    }

    /**
     * 初始化自定义导出 (V2支持导出自定义字段)
     *
     * @param list             导出的集合
     * @param sheetName        表单名
     * @param type             类型
     * @param selectProperties 选中的字段名集合,不传默认导出所有默认字段
     */
    public void initV2(List<T> list, String sheetName, Type type, List<String> selectProperties) {
        if (list == null) {
            list = new ArrayList<>();
        }
        this.list = list;
        this.sheetName = FileUtils.replaceFileNameUnValidChar(sheetName);
        this.type = type;
        this.isCustom = CollectionUtils.isNotEmpty(selectProperties);
        this.selectedProperties = selectProperties;
        createExcelFieldV2();
        createWorkbook();
    }


    /**
     * 对excel表单默认第一个索引名转换成list
     *
     * @param is 输入流
     * @return 转换后集合
     */
    public List<T> importExcel(InputStream is) throws Exception {
        return importExcel(StringUtils.EMPTY, is);
    }

    /**
     * 对excel表单指定表格索引名转换成list
     *
     * @param sheetName 表格索引名
     * @param is        输入流
     * @return 转换后集合
     */
    public List<T> importExcel(String sheetName, InputStream is) throws IOException, InvalidFormatException, IllegalAccessException, InstantiationException {
        this.type = Type.IMPORT;
        this.wb = WorkbookFactory.create(is);
        List<T> arrayList = new ArrayList<>();
        Sheet mySheet;
        if (StringUtils.isNotEmpty(sheetName)) {
            // 如果指定sheet名,则取指定sheet中的内容.
            mySheet = wb.getSheet(sheetName);
        } else {
            // 如果传入的sheet名不存在则默认指向第1个sheet.
            mySheet = wb.getSheetAt(0);
        }

        if (mySheet == null) {
            throw new IOException("文件sheet不存在");
        }

        int rows = mySheet.getPhysicalNumberOfRows();

        if (rows > 0) {
            // 定义一个map用于存放excel列的序号和field.
            Map<String, Integer> cellMap = new HashMap<>();
            // 获取表头
            Row heard = mySheet.getRow(0);
            for (int i = 0; i < heard.getPhysicalNumberOfCells(); i++) {
                Cell cell = heard.getCell(i);
                if (StringUtils.isNotNull(cell)) {
                    String value = this.getCellValue(heard, i).toString();
                    cellMap.put(value, i);
                } else {
                    cellMap.put(null, i);
                }
            }
            // 有数据时才处理 得到类的所有field.
            Field[] allFields = clazz.getDeclaredFields();
            // 定义一个map用于存放列的序号和field.
            Map<Integer, Field> fieldsMap = new HashMap<>();
            for (int col = 0; col < allFields.length; col++) {
                Field field = allFields[col];
                Excel attr = field.getAnnotation(Excel.class);
                if (attr != null && (attr.type() == Type.ALL || attr.type() == type)) {
                    // 设置类的私有字段属性可访问.
                    field.setAccessible(true);
                    Integer column = cellMap.get(attr.name());
                    if (column != null) {
                        fieldsMap.put(column, field);
                    }
                }
            }
            for (int i = 1; i < rows; i++) {
                // 从第2行开始取数据,默认第一行是表头.
                Row row = mySheet.getRow(i);
                T entity = null;
                for (Map.Entry<Integer, Field> entry : fieldsMap.entrySet()) {
                    Object val = this.getCellValue(row, entry.getKey());

                    // 如果不存在实例则新建.
                    entity = (entity == null ? clazz.newInstance() : entity);
                    // 从map中得到对应列的field.
                    Field field = fieldsMap.get(entry.getKey());
                    // 取得类型,并根据对象类型设置值.
                    Class<?> fieldType = field.getType();
                    if (String.class == fieldType) {
                        String s = Convert.toStr(val);
                        if (StringUtils.endsWith(s, ".0")) {
                            val = StringUtils.substringBefore(s, ".0");
                        } else {
                            val = Convert.toStr(val);
                        }
                    } else if ((Integer.TYPE == fieldType) || (Integer.class == fieldType)) {
                        val = Convert.toInt(val);
                    } else if ((Long.TYPE == fieldType) || (Long.class == fieldType)) {
                        val = Convert.toLong(val);
                    } else if ((Double.TYPE == fieldType) || (Double.class == fieldType)) {
                        val = Convert.toDouble(val);
                    } else if ((Float.TYPE == fieldType) || (Float.class == fieldType)) {
                        val = Convert.toFloat(val);
                    } else if (BigDecimal.class == fieldType) {
                        val = Convert.toBigDecimal(val);
                    } else if (Date.class == fieldType) {
                        if (val instanceof String) {
                            val = DateUtils.parseDate(val);
                        } else if (val instanceof Double) {
                            val = DateUtil.getJavaDate((Double) val);
                        }
                    }
                    if (StringUtils.isNotNull(fieldType)) {
                        Excel attr = field.getAnnotation(Excel.class);
                        String propertyName = field.getName();
                        if (StringUtils.isNotEmpty(attr.targetAttr())) {
                            propertyName = field.getName() + "." + attr.targetAttr();
                        } else if (StringUtils.isNotEmpty(attr.readConverterExp())) {
                            val = reverseByExp(Convert.toStr(val), attr.readConverterExp(), attr.separator());
                        } else if (StringUtils.isNotEmpty(attr.dictType())) {
                            val = reverseDictByExp(Convert.toStr(val), attr.dictType(), attr.separator());
                        }
                        ReflectUtils.invokeSetter(entity, propertyName, val);
                    }
                }
                arrayList.add(entity);
            }
        }
        return arrayList;
    }

    /**
     * 对list数据源将其里面的数据导入到excel表单
     *
     * @param list      导出数据集合
     * @param sheetName 工作表的名称
     * @return 结果
     */
    public AjaxResult exportExcel(List<T> list, String sheetName) {
        this.init(list, sheetName, Type.EXPORT);
        return exportExcel();
    }

    /**
     * 导出excel V2
     *
     * @param list               导出的列表
     * @param sheetName          表单名
     * @param selectedProperties 指定的字段名集合
     * @return excel文件名
     */
    public AjaxResult exportExcelV2(List<T> list, String sheetName, List<String> selectedProperties) {
        this.initV2(list, sheetName, Type.EXPORT, selectedProperties);
        return exportExcelV2();
    }

    /**
     * 导出excel 可导出引入注解的字段和拓展字段
     * list中需要有扩展字段且命名为 Map<String, String> extendPropMapper K:列名,V值
     *
     * @param list               导出的列表 示例 List<FormCustomerOperRecordExportVO> {@link com.easyink.wecom.domain.vo.form.FormCustomerOperRecordExportVO}
     * @param sheetName          表单名
     * @param extendProperties   拓展的字段名集合 extendPropMapper K:列名组成的集合
     * @return excel文件名
     */
    public AjaxResult exportExcelDefinedAndExtProp(List<T> list, String sheetName, List<String> extendProperties) {
        this.initV2(list, sheetName, Type.EXPORT, extendProperties);
        return exportExcelDefinedAndExtProp();
    }

    /**
     * 对list数据源将其里面的数据导入到excel表单
     *
     * @param sheetName 工作表的名称
     * @return 结果
     */
    public AjaxResult importTemplateExcel(String sheetName) {
        this.init(null, sheetName, Type.IMPORT);
        return exportExcel();
    }

    /**
     * 对list数据源将其里面的数据导入到excel表单
     *
     * @return 结果
     */
    public AjaxResult exportExcel() {
        OutputStream out = null;
        try {
            // 取出一共有多少个sheet.
            double sheetNo = Math.ceil((double) list.size() / SHEET_SIZE);
            for (int index = 0; index < sheetNo; index++) {
                createSheet(sheetNo, index);

                // 产生一行
                Row row = sheet.createRow(0);
                int column = 0;
                // 写入各个字段的列头名称
                for (Object[] os : fields) {
                    Excel excel = (Excel) os[1];
                    this.createCell(excel, row, column++);
                }
                if (Type.EXPORT.equals(type)) {
                    fillExcelData(index);
                }
            }
            String filename = encodingFilename(sheetName);
            out = new FileOutputStream(getAbsoluteFile(filename));
            wb.write(out);
            return AjaxResult.success(filename);
        } catch (Exception e) {
            log.error("导出Excel异常{}", ExceptionUtils.getStackTrace(e));
            throw new CustomException("导出Excel失败，请联系网站管理员！");
        } finally {
            if (wb != null) {
                try {
                    wb.close();
                } catch (IOException e1) {
                    log.error("异常信息:{}", e1.getMessage());
                }
            }
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e1) {
                    log.error("异常信息:{}", e1.getMessage());
                }
            }
        }
    }

    /**
     * 对list数据源将其里面的数据导入到excel表单(第二版)
     *
     * @return 结果
     */
    public AjaxResult exportExcelV2() {
        OutputStream out = null;
        try {
            // 取出一共有多少个sheet.
            double sheetNo = Math.ceil((double) list.size() / SHEET_SIZE);
            for (int index = 0; index < sheetNo; index++) {
                createSheet(sheetNo, index);

                // 产生一行
                Row row = sheet.createRow(0);
                int column = 0;
                // 写入各个字段的列头名称
                if (isCustom) {
                    for (String colName : selectedProperties) {
                        this.createCell(colName, row, column++);
                    }
                } else {
                    for (Object[] os : fields) {
                        Excel excel = (Excel) os[1];
                        this.createCell(excel, row, column++);
                    }
                }
                if (Type.EXPORT.equals(type)) {
                    fillExcelDataV2(index);
                }
            }
            String filename = encodingFilename(sheetName);
            out = new FileOutputStream(getAbsoluteFile(filename));
            wb.write(out);
            return AjaxResult.success(filename);
        } catch (Exception e) {
            log.error("导出Excel异常{}", ExceptionUtils.getStackTrace(e));
            throw new CustomException("导出Excel失败，请联系网站管理员！");
        } finally {
            if (wb != null) {
                try {
                    wb.close();
                } catch (IOException e1) {
                    log.error("异常信息:{}", e1.getMessage());
                }
            }
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e1) {
                    log.error("异常信息:{}", e1.getMessage());
                }
            }
        }
    }

    /**
     * 写入各个字段的列头名称（包括引入注解的属性和扩展属性）
     *
     * @return
     */
    public AjaxResult exportExcelDefinedAndExtProp(){
        OutputStream out = null;
        try {
            // 取出一共有多少个sheet.
            double sheetNo = Math.ceil((double) list.size() / SHEET_SIZE);
            for (int index = 0; index < sheetNo; index++) {
                createSheet(sheetNo, index);

                // 产生一行
                Row row = sheet.createRow(0);
                int column = 0;
                // 写入各个字段的列头名称
                for (Object[] os : fields) {
                    Excel excel = (Excel) os[1];
                    this.createCell(excel, row, column++);
                }
                if (isCustom) {
                    for (String colName : selectedProperties) {
                        this.createCell(colName, row, column++);
                    }
                }
                if (Type.EXPORT.equals(type)) {
                    fillExcelDefinedAndExtProp(index);
                }
            }
            String filename = encodingFilename(sheetName);
            out = new FileOutputStream(getAbsoluteFile(filename));
            wb.write(out);
            return AjaxResult.success(filename);
        } catch (Exception e) {
            log.error("导出Excel异常{}", ExceptionUtils.getStackTrace(e));
            throw new CustomException("导出Excel失败，请联系网站管理员！");
        } finally {
            if (wb != null) {
                try {
                    wb.close();
                } catch (IOException e1) {
                    log.error("异常信息:{}", e1.getMessage());
                }
            }
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e1) {
                    log.error("异常信息:{}", e1.getMessage());
                }
            }
        }
    }

    /**
     * 填充excel数据
     *
     * @param index 序号
     */
    public void fillExcelData(int index) {
        int startNo = index * SHEET_SIZE;
        int endNo = Math.min(startNo + SHEET_SIZE, list.size());
        Row row;
        for (int i = startNo; i < endNo; i++) {
            row = sheet.createRow(i + 1 - startNo);
            // 得到导出对象.
            T vo = list.get(i);
            int column = 0;
            for (Object[] os : fields) {
                Field field = (Field) os[0];
                Excel excel = (Excel) os[1];
                // 设置实体类私有属性可访问
                field.setAccessible(true);
                this.addCell(excel, row, vo, field, column++);
            }
        }
    }

    /**
     * 填充表单数据
     *
     * @param index 索引
     * @throws IllegalAccessException
     * @throws NoSuchFieldException
     */
    public void fillExcelDataV2(int index) throws IllegalAccessException, NoSuchFieldException {
        int startNo = index * SHEET_SIZE;
        int endNo = Math.min(startNo + SHEET_SIZE, list.size());
        Row row;
        for (int i = startNo; i < endNo; i++) {
            row = sheet.createRow(i + 1 - startNo);
            // 得到导出对象.
            T vo = list.get(i);
            int column = 0;
            for (Object[] os : fields) {
                Field field = (Field) os[0];
                Excel excel = (Excel) os[1];
                field.setAccessible(true);
                // 非自定义导出 或者 指定导出该字段才填充该数据
                if (!isCustom || selectedProperties.contains(excel.name())) {
                    // 设置实体类私有属性可访问
                    this.addCell(excel, row, vo, field, column++);
                }
            }
            // 自定义导出:填充自定义字段
            if (isCustom) {
                //通过反射获取扩展字段映射
                Field mapperField = vo.getClass().getDeclaredField("extendPropMapper");
                mapperField.setAccessible(true);
                Map<String, String> map = (Map<String, String>) mapperField.get(vo);
                if (map == null) {
                    continue;
                }
                for (Map.Entry<String, String> entry : map.entrySet()) {
                    // 当前列的字段
                    this.addCell(row, entry.getValue(), column++);
                }
            }
        }
    }

    /**
     * 填充表单数据 引入注解的字段和拓展字段
     *
     * @param index 索引
     * @throws IllegalAccessException
     * @throws NoSuchFieldException
     */
    public void fillExcelDefinedAndExtProp(int index) throws IllegalAccessException, NoSuchFieldException {
        int startNo = index * SHEET_SIZE;
        int endNo = Math.min(startNo + SHEET_SIZE, list.size());
        Row row;
        for (int i = startNo; i < endNo; i++) {
            row = sheet.createRow(i + 1 - startNo);
            // 得到导出对象.
            T vo = list.get(i);
            int column = 0;
            for (Object[] os : fields) {
                Field field = (Field) os[0];
                field.setAccessible(true);
                // 设置实体类私有属性可访问
                Excel excel = (Excel) os[1];
                this.addCell(excel, row, vo, field, column++);
            }
            // 自定义导出:填充自定义字段
            if (isCustom) {
                //通过反射获取扩展字段映射
                Field mapperField = vo.getClass().getDeclaredField("extendPropMapper");
                mapperField.setAccessible(true);
                Map<String, String> map = (Map<String, String>) mapperField.get(vo);
                if (map == null) {
                    continue;
                }
                for (Map.Entry<String, String> entry : map.entrySet()) {
                    // 当前列的字段
                    this.addCell(row, entry.getValue(), column++);
                }
            }
        }
    }


    /**
     * 创建表格样式
     *
     * @param wb 工作薄对象
     * @return 样式列表
     */
    private Map<String, CellStyle> createStyles(Workbook wb) {
        // 写入各条记录,每条记录对应excel表中的一行
        Map<String, CellStyle> styleMap = new HashMap<>();
        CellStyle style = wb.createCellStyle();
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderRight(BorderStyle.THIN);
        style.setRightBorderColor(IndexedColors.GREY_50_PERCENT.getIndex());
        style.setBorderLeft(BorderStyle.THIN);
        style.setLeftBorderColor(IndexedColors.GREY_50_PERCENT.getIndex());
        style.setBorderTop(BorderStyle.THIN);
        style.setTopBorderColor(IndexedColors.GREY_50_PERCENT.getIndex());
        style.setBorderBottom(BorderStyle.THIN);
        style.setBottomBorderColor(IndexedColors.GREY_50_PERCENT.getIndex());
        Font dataFont = wb.createFont();
        dataFont.setFontName("Arial");
        dataFont.setFontHeightInPoints((short) 10);
        style.setFont(dataFont);
        styleMap.put("data", style);

        style = wb.createCellStyle();
        style.cloneStyleFrom(styleMap.get("data"));
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setFillForegroundColor(IndexedColors.GREY_50_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font headerFont = wb.createFont();
        headerFont.setFontName("Arial");
        headerFont.setFontHeightInPoints((short) 10);
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(headerFont);
        styleMap.put("header", style);

        return styleMap;
    }

    /**
     * 创建单元格
     */
    public Cell createCell(Excel attr, Row row, int column) {
        // 创建列
        Cell cell = row.createCell(column);
        // 写入列信息
        cell.setCellValue(attr.name());
        setDataValidation(attr, row, column);
        cell.setCellStyle(styles.get("header"));
        return cell;
    }

    /**
     * 创建单元格
     *
     * @param name   单元格名称
     * @param row    行
     * @param column 列
     * @return {@link Cell }
     */
    public Cell createCell(String name, Row row, int column) {
        // 创建列
        Cell cell = row.createCell(column);
        // 写入列信息
        cell.setCellValue(name);
        setDataValidation(name, row, column);
        cell.setCellStyle(styles.get("header"));
        return cell;
    }

    /**
     * 设置单元格信息
     *
     * @param value 单元格值
     * @param attr  注解相关
     * @param cell  单元格信息
     */
    public void setCellVo(Object value, Excel attr, Cell cell) {
        if (ColumnType.STRING == attr.cellType()) {
            cell.setCellType(CellType.STRING);
            cell.setCellValue(getStringCellValue(value));
        } else if (ColumnType.NUMERIC == attr.cellType()) {
            cell.setCellType(CellType.NUMERIC);
            cell.setCellValue(Integer.parseInt(value + ""));
        }
    }

    /**
     * 获得String类型的单元格内的值
     *
     * @param value 单元格的值
     * @return
     */
    String getStringCellValue(Object value) {
        if (value == null) {
            return StringUtils.EMPTY;
        }
        String cellValue = String.valueOf(value);
        if (isContainCSVInjectChar(cellValue)) {
            return Constants.TAB + cellValue;
        }
        return String.valueOf(value);
    }

    /**
     * 是否包含可能会引起CSV注入 的特殊字符序列
     *
     * @param cellValue 单元格的值
     * @return
     */
    boolean isContainCSVInjectChar(String cellValue) {
        String[] specialStr = Constants.getCsvInjectCharList();
        for (String str : specialStr) {
            if (cellValue.contains(str)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 创建表格样式
     */
    public void setDataValidation(Excel attr, Row row, int column) {
        String columnString = "注：";
        if (attr.name().indexOf(columnString) >= 0) {
            sheet.setColumnWidth(column, 6000);
        } else {
            // 设置列宽
            sheet.setColumnWidth(column, (int) ((attr.width() + 0.72) * 256));
            row.setHeight((short) (attr.height() * 20));
        }
        // 如果设置了提示信息则鼠标放上去提示.
        if (StringUtils.isNotEmpty(attr.prompt())) {
            // 这里默认设了2-101列提示.
            setXSSFPrompt(sheet, "", attr.prompt(), 1, 100, column, column);
        }
        // 如果设置了combo属性则本列只能选择不能输入
        if (attr.combo().length > 0) {
            // 这里默认设了2-101列只能选择不能输入.
            setXSSFValidation(sheet, attr.combo(), 1, 100, column, column);
        }
    }

    /**
     * 创建表格样式
     */
    public void setDataValidation(String colName, Row row, int column) {
        String columnString = "注：";
        if (colName.indexOf(columnString) >= 0) {
            sheet.setColumnWidth(column, 6000);
        } else {
            // 设置列宽
            sheet.setColumnWidth(column, (int) ((16 + 0.72) * 256));
            row.setHeight((short) (14 * 20));
        }
    }

    /**
     * 添加单元格
     */
    public Cell addCell(Excel attr, Row row, T vo, Field field, int column) {
        Cell cell = null;
        try {
            // 设置行高
            row.setHeight((short) (attr.height() * 20));
            // 根据Excel中设置情况决定是否导出,有些情况需要保持为空,希望用户填写这一列.
            if (attr.isExport()) {
                // 创建cell
                cell = row.createCell(column);
                cell.setCellStyle(styles.get("data"));

                // 用于读取对象中的属性
                Object value = getTargetValue(vo, field, attr);
                String dateFormat = attr.dateFormat();
                String readConverterExp = attr.readConverterExp();
                String separator = attr.separator();
                String dictType = attr.dictType();
                if (StringUtils.isNotEmpty(dateFormat) && StringUtils.isNotNull(value)) {
                    cell.setCellValue(DateUtils.parseDateToStr(dateFormat, (Date) value));
                } else if (StringUtils.isNotEmpty(readConverterExp) && StringUtils.isNotNull(value)) {
                    cell.setCellValue(convertByExp(Convert.toStr(value), readConverterExp, separator));
                } else if (StringUtils.isNotEmpty(dictType) && StringUtils.isNotNull(value)) {
                    cell.setCellValue(convertDictByExp(Convert.toStr(value), dictType, separator));
                } else if (value instanceof BigDecimal && -1 != attr.scale()) {
                    cell.setCellValue((((BigDecimal) value).setScale(attr.scale(), attr.roundingMode())).toString());
                } else {
                    // 设置列类型
                    setCellVo(value, attr, cell);
                }
            }
        } catch (Exception e) {
            log.error("导出Excel失败 e:{}", ExceptionUtils.getStackTrace(e));
        }
        return cell;
    }


    /**
     * 添加单元格
     */
    public Cell addCell(Row row, String value, int column) {
        Cell cell = null;
        try {
            // 设置行高
            row.setHeight((short) (14 * 20));
            // 创建cell
            cell = row.createCell(column);
            cell.setCellStyle(styles.get("data"));
            // 用于读取对象中的属性
            cell.setCellType(CellType.STRING);
            cell.setCellValue(getStringCellValue(value));

        } catch (Exception e) {
            log.error("导出Excel失败 e:{}", ExceptionUtils.getStackTrace(e));
        }
        return cell;
    }

    /**
     * 设置 POI XSSFSheet 单元格提示
     *
     * @param sheet         表单
     * @param promptTitle   提示标题
     * @param promptContent 提示内容
     * @param firstRow      开始行
     * @param endRow        结束行
     * @param firstCol      开始列
     * @param endCol        结束列
     */
    public void setXSSFPrompt(Sheet sheet, String promptTitle, String promptContent, int firstRow, int endRow,
                              int firstCol, int endCol) {
        DataValidationHelper helper = sheet.getDataValidationHelper();
        DataValidationConstraint constraint = helper.createCustomConstraint("DD1");
        CellRangeAddressList regions = new CellRangeAddressList(firstRow, endRow, firstCol, endCol);
        DataValidation dataValidation = helper.createValidation(constraint, regions);
        dataValidation.createPromptBox(promptTitle, promptContent);
        dataValidation.setShowPromptBox(true);
        sheet.addValidationData(dataValidation);
    }

    /**
     * 设置某些列的值只能输入预制的数据,显示下拉框.
     *
     * @param sheet    要设置的sheet.
     * @param textlist 下拉框显示的内容
     * @param firstRow 开始行
     * @param endRow   结束行
     * @param firstCol 开始列
     * @param endCol   结束列
     * @return 设置好的sheet.
     */
    public void setXSSFValidation(Sheet sheet, String[] textlist, int firstRow, int endRow, int firstCol, int endCol) {
        DataValidationHelper helper = sheet.getDataValidationHelper();
        // 加载下拉列表内容
        DataValidationConstraint constraint = helper.createExplicitListConstraint(textlist);
        // 设置数据有效性加载在哪个单元格上,四个参数分别是：起始行、终止行、起始列、终止列
        CellRangeAddressList regions = new CellRangeAddressList(firstRow, endRow, firstCol, endCol);
        // 数据有效性对象
        DataValidation dataValidation = helper.createValidation(constraint, regions);
        // 处理Excel兼容性问题
        if (dataValidation instanceof XSSFDataValidation) {
            dataValidation.setSuppressDropDownArrow(true);
            dataValidation.setShowErrorBox(true);
        } else {
            dataValidation.setSuppressDropDownArrow(false);
        }

        sheet.addValidationData(dataValidation);
    }

    /**
     * 解析导出值 0=男,1=女,2=未知
     *
     * @param propertyValue 参数值
     * @param converterExp  翻译注解
     * @param separator     分隔符
     * @return 解析后值
     */
    public static String convertByExp(String propertyValue, String converterExp, String separator) {
        StringBuilder propertyString = new StringBuilder();
        String[] convertSource = converterExp.split(",");
        for (String item : convertSource) {
            String[] itemArray = item.split("=");
            if (StringUtils.containsAny(separator, propertyValue)) {
                for (String value : propertyValue.split(separator)) {
                    if (itemArray[0].equals(value)) {
                        propertyString.append(itemArray[1] + separator);
                        break;
                    }
                }
            } else {
                if (itemArray[0].equals(propertyValue)) {
                    return itemArray[1];
                }
            }
        }
        return StringUtils.stripEnd(propertyString.toString(), separator);
    }

    /**
     * 反向解析值 男=0,女=1,未知=2
     *
     * @param propertyValue 参数值
     * @param converterExp  翻译注解
     * @param separator     分隔符
     * @return 解析后值
     */
    public static String reverseByExp(String propertyValue, String converterExp, String separator) {
        StringBuilder propertyString = new StringBuilder();
        String[] convertSource = converterExp.split(",");
        for (String item : convertSource) {
            String[] itemArray = item.split("=");
            if (StringUtils.containsAny(separator, propertyValue)) {
                for (String value : propertyValue.split(separator)) {
                    if (itemArray[1].equals(value)) {
                        propertyString.append(itemArray[0] + separator);
                        break;
                    }
                }
            } else {
                if (itemArray[1].equals(propertyValue)) {
                    return itemArray[0];
                }
            }
        }
        return StringUtils.stripEnd(propertyString.toString(), separator);
    }

    /**
     * 解析字典值
     *
     * @param dictValue 字典值
     * @param dictType  字典类型
     * @param separator 分隔符
     * @return 字典标签
     */
    public static String convertDictByExp(String dictValue, String dictType, String separator) {
        return DictUtils.getDictLabel(dictType, dictValue, separator);
    }

    /**
     * 反向解析值字典值
     *
     * @param dictLabel 字典标签
     * @param dictType  字典类型
     * @param separator 分隔符
     * @return 字典值
     */
    public static String reverseDictByExp(String dictLabel, String dictType, String separator) {
        return DictUtils.getDictValue(dictType, dictLabel, separator);
    }

    /**
     * 编码文件名
     */
    public String encodingFilename(String filename) {
        filename = UUID.randomUUID().toString() + "_" + filename + ".xlsx";
        return filename;
    }

    /**
     * 获取下载路径
     *
     * @param filename 文件名称
     */
    public String getAbsoluteFile(String filename) {
        String downloadPath = RuoYiConfig.getDownloadPath() + filename;
        File desc = new File(downloadPath);
        if (!desc.getParentFile().exists()) {
            desc.getParentFile().mkdirs();
        }
        return downloadPath;
    }

    /**
     * 获取bean中的属性值
     *
     * @param vo    实体对象
     * @param field 字段
     * @param excel 注解
     * @return 最终的属性值
     * @throws Exception
     */
    private Object getTargetValue(T vo, Field field, Excel excel) throws Exception {
        String dot = ".";
        Object o = field.get(vo);
        if (StringUtils.isNotEmpty(excel.targetAttr())) {
            String target = excel.targetAttr();
            if (target.indexOf(dot) > -1) {
                String[] targets = target.split("[.]");
                for (String name : targets) {
                    o = getValue(o, name);
                }
            } else {
                o = getValue(o, target);
            }
        }
        return o;
    }

    /**
     * 以类的属性的get方法方法形式获取值
     *
     * @param o
     * @param name
     * @return value
     * @throws Exception
     */
    private Object getValue(Object o, String name) throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
        if (StringUtils.isNotEmpty(name)) {
            Class<?> aClass = o.getClass();
            String methodName = "get" + name.substring(0, 1).toUpperCase() + name.substring(1);
            Method method = aClass.getMethod(methodName);
            o = method.invoke(o);
        }
        return o;
    }

    /**
     * 得到所有定义字段
     */
    private void createExcelField() {
        this.fields = new ArrayList<>();
        List<Field> tempFields = new ArrayList<>();
        tempFields.addAll(Arrays.asList(clazz.getSuperclass().getDeclaredFields()));
        tempFields.addAll(Arrays.asList(clazz.getDeclaredFields()));
        for (Field field : tempFields) {
            // 单注解
            if (field.isAnnotationPresent(Excel.class)) {
                putToField(field, field.getAnnotation(Excel.class));
            }

            // 多注解
            if (field.isAnnotationPresent(Excels.class)) {
                Excels attrs = field.getAnnotation(Excels.class);
                Excel[] excels = attrs.value();
                for (Excel excel : excels) {
                    putToField(field, excel);
                }
            }
        }
        this.fields = this.fields.stream().sorted(Comparator.comparing(objects -> ((Excel) objects[1]).sort())).collect(Collectors.toList());
    }

    /**
     * 创建excel的行标题(第二版:支持自定义导出字段)
     */
    private void createExcelFieldV2() {
        this.fields = new ArrayList<>();
        List<Field> tempFields = new ArrayList<>();
        tempFields.addAll(Arrays.asList(clazz.getSuperclass().getDeclaredFields()));
        tempFields.addAll(Arrays.asList(clazz.getDeclaredFields()));
        for (Field field : tempFields) {
            // 单注解
            if (field.isAnnotationPresent(Excel.class)) {
                putToField(field, field.getAnnotation(Excel.class));
            }
            // 多注解
            if (field.isAnnotationPresent(Excels.class)) {
                Excels attrs = field.getAnnotation(Excels.class);
                Excel[] excels = attrs.value();
                for (Excel excel : excels) {
                    //  非自定义导出 或者 包含导出字段时才填充相应的表头
                    if (!isCustom || selectedProperties.contains(excel.name())) {
                        putToField(field, excel);
                    }
                }
            }
        }
        this.fields = this.fields.stream().sorted(Comparator.comparing(objects -> ((Excel) objects[1]).sort())).collect(Collectors.toList());
    }


    /**
     * 放到字段集合中
     */
    private void putToField(Field field, Excel attr) {
        if (attr != null && (attr.type() == Type.ALL || attr.type() == type)) {
            this.fields.add(new Object[]{field, attr});
        }
    }

    /**
     * 创建一个工作簿
     */
    public void createWorkbook() {
        this.wb = new SXSSFWorkbook(500);
    }

    /**
     * 创建工作表
     *
     * @param sheetNo sheet数量
     * @param index   序号
     */
    public void createSheet(double sheetNo, int index) {
        this.sheet = wb.createSheet();
        this.styles = createStyles(wb);
        // 设置工作表的名称.
        if (sheetNo == 0) {
            wb.setSheetName(index, sheetName);
        } else {
            wb.setSheetName(index, sheetName + index);
        }
    }

    /**
     * 获取单元格值
     *
     * @param row    获取的行
     * @param column 获取单元格列号
     * @return 单元格值
     */
    public Object getCellValue(Row row, int column) {
        if (row == null) {
            return row;
        }
        Object val = "";
        try {
            Cell cell = row.getCell(column);
            if (StringUtils.isNotNull(cell)) {
                if (cell.getCellTypeEnum() == CellType.NUMERIC || cell.getCellTypeEnum() == CellType.FORMULA) {
                    val = cell.getNumericCellValue();
                    if (HSSFDateUtil.isCellDateFormatted(cell)) {
                        val = DateUtil.getJavaDate((Double) val); // POI Excel 日期格式转换
                    } else {
                        if ((Double) val % 1 > 0) {
                            val = new BigDecimal(val.toString());
                        } else {
                            val = new DecimalFormat("0").format(val);
                        }
                    }
                } else if (cell.getCellTypeEnum() == CellType.STRING) {
                    val = cell.getStringCellValue();
                } else if (cell.getCellTypeEnum() == CellType.BOOLEAN) {
                    val = cell.getBooleanCellValue();
                } else if (cell.getCellTypeEnum() == CellType.ERROR) {
                    val = cell.getErrorCellValue();
                }

            }
        } catch (Exception e) {
            return val;
        }
        return val;
    }
}