#!/usr/bin/env groovy

import groovy.sql.Sql
import org.apache.poi.xssf.usermodel.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.usermodel.*
import org.apache.poi.hssf.usermodel.*;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;


xlsfile1 = args[0];
xlsfile2 = args[1];
xlsfile3 = args[2];
println "Appending $xlsfile1,$xlsfile2 -> $xlsfile3"

InputStream in1 = new FileInputStream(xlsfile1);
Workbook wb1 = WorkbookFactory.create(in1);
in1.close();
Sheet sheet1 = wb1.getSheetAt(0);
InputStream in2 = new FileInputStream(xlsfile2);
Workbook wb2 = WorkbookFactory.create(in2);
in2.close();
Sheet sheet2 = wb2.getSheetAt(0);

// work out last line of sheet1
endrow = sheet1.getLastRowNum();
println "Sheet1 has $endrow lines...";
// create separator
endrow++;
nextrow = sheet1.createRow(endrow);
cell = nextrow.createCell(0) ;
cell.setCellValue("===APPEND===");

nrows2 = sheet2.getLastRowNum();
println "Sheet2 has $nrows2 lines...";
for(rnum=0; rnum<nrows2; rnum++) {
	endrow++;
	newrow = sheet1.createRow(endrow);
	print "append to row $endrow : ";
	row2 = sheet2.getRow(rnum);
	if(row2 == null) {
		continue;
	}
	ncells2 = row2.getLastCellNum();
	for(cnum=0;cnum<ncells2;cnum++) {
		c  = row2.getCell(cnum) ;
		if(c == null) {
			print ",";
		} else {
			val = null
 			switch (c.getCellType()) {
 			case Cell.CELL_TYPE_BOOLEAN:
 				val = c.getBooleanCellValue();
 				break;
 			case Cell.CELL_TYPE_NUMERIC:
 				val = c.getNumericCellValue();
 				break;
 			case Cell.CELL_TYPE_STRING:
 				val = c.getStringCellValue();
				break;
 			case Cell.CELL_TYPE_BLANK:
				break;
 			case Cell.CELL_TYPE_ERROR:
 				val = c.getErrorCellValue();
 				break;
			}
			print "$val,";
			// now append this cell to the new row
			newcell = newrow.createCell(cnum);
			newcell.setCellValue(val);
		}
	}
	println "";
}

outfilename = xlsfile3;
println "Saving output file $outfilename..."
FileOutputStream xlsout = new FileOutputStream(outfilename)
wb1.write(xlsout)
xlsout.close()

println "Done"
