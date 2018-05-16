#!/usr/bin/env groovy

import groovy.sql.Sql
import org.apache.poi.xssf.usermodel.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.usermodel.*
import org.apache.poi.hssf.usermodel.*;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

year=2018
//BB_TEMPLATE='tv1_2015'
//BB_TEMPLATE='dut_temp_2016'
BB_TEMPLATE=args[1]
println "Default Course Template ID=$BB_TEMPLATE"
DSK_COURSES="DUT_COURSES_$year" as String
DSK_STAFF="DUT_STAFF_$year" as String
DSK_STUDENTS="DUT_STUDENTS_$year" as String
DSK_LECTENROL="DUT_LECTENROL_$year" as String
DSK_STUDENROL="DUT_STUDENROL_$year" as String


//
// Code to read in spreadsheet...
//
infilename = args[0]
InputStream ins = new FileInputStream(infilename)
Workbook wb = WorkbookFactory.create(ins)
Sheet sheet = wb.getSheetAt(0)

sheetrows = []

done = false
rowndx = 0
consnullrows = 0
while(!done) {
  Row row = sheet.getRow(rowndx)
  if(row == null) {
    consnullrows++
    if(consnullrows>5) {
      done = true
    }
  } else {
    consnullrows = 0
    parseRow(rowndx,row)
  }
  rowndx++
}

def parseRow(ndx,r) {
  rowvals = []
//  print "\nRow$ndx: "
  cellndx = 0
  consnullcells = 0
  rowdone = false
  while(!rowdone) {
    Cell cell = r.getCell(cellndx)
    if(cell == null) {
//      print "<> "
      val = null
      consnullcells++
      if(consnullcells>3) {
        rowdone = true
      }
    } else {
      consnullcells=0
      val = null
      switch (cell.getCellType()) {
        case Cell.CELL_TYPE_BOOLEAN:
            val = cell.getBooleanCellValue();
            break;
        case Cell.CELL_TYPE_NUMERIC:
            val = cell.getNumericCellValue();
            break;
        case Cell.CELL_TYPE_STRING:
            val = cell.getStringCellValue();
            break;
        case Cell.CELL_TYPE_BLANK:
            break;
        case Cell.CELL_TYPE_ERROR:
            val = cell.getErrorCellValue();
            break;
        default:
            print "...celltype=${cell.getCellType()}"
            val = null
      }
    }
//    print "$cellndx: $val "
    rowvals.add(val)
    cellndx++
  }
  sheetrows.add(rowvals)
}

//print "sheetrows=$sheetrows"

// 
// END OF code to read sheet
///


//url = "jdbc:oracle:thin:@neptune.dut.ac.za:1527:PRODI03"
//url = "jdbc:oracle:thin:@10.0.4.16:1527:PRODI03"
url = "jdbc:oracle:thin:@10.0.4.16:1810:PRODI04"
user = "ilm"
pass = "identity"

db = Sql.newInstance(url,user,pass)


print "...PROCESSING:"
bb_classrooms = [:]
itssubjlist = []

def getSubjDesc(scode) {
  if(bb_classrooms[itscode] == null) {
    qs = "select * from STUD.IALSUB where IALSUBJ=$itscode and IALCYR=$year"
    //println "$qs"
    db.eachRow(qs) { r2 ->
      // we know this is a valid ITS subject code...should cache this
      itsdesc = r2['IALDESC']
      println "GetSubjDesc...$scode:IALDESC=$itsdesc"
      if(bb_classrooms[itscode] == null) {
          itssubjlist += itscode
          bb_classrooms[itscode] = ['code':itscode,'desc':itsdesc,'bbcoursemap':[:]]
      }
    }
  }
  return bb_classrooms[itscode]
}

def checkRow(itscode,bbc) {
  print "checkRow($itscode,$bbc)";
  if(itscode == null || bbc == null) {
    return false
  } else {
    if(bbc instanceof String && bbc.startsWith(itscode)) {
      println "...row OK!";
      return true
    }
  }
  return false
}

sheetrows.each() { r ->
  //println "...Row=$r"
  itscode = r[2]
  bbc = r[0]
  if(checkRow(itscode,bbc)) {
    println "...process row=$r"
    classinfo = getSubjDesc(itscode)
    //classinfo = bb_classrooms[itscode]
    if(classinfo != null) {
      bb_classroom = r[0]

      bb_template = r[1] ?: BB_TEMPLATE

      if(classinfo.bbcoursemap[bb_classroom] == null) {
        classinfo.bbcoursemap[bb_classroom] = ['bbcode':bb_classroom,'bbtemp':bb_template,'lecturers': new HashSet()]
      }
      try {
        lecturer_list = r[11]
        match = (lecturer_list =~ /(\d){8}/)
        match.each() { m ->
          classinfo.bbcoursemap[bb_classroom].lecturers.add(m[0])
        }
      } catch (Exception x) {
          println "Exception - "+x
          println "lecturer_list=$lecturer_list"
      }
    }
  }
}

courses_out = []
courses_out << ['EXTERNAL_COURSE_KEY','COURSE_ID','COURSE_NAME','TEMPLATE_COURSE_KEY','AVAILABLE_IND','ROW_STATUS','DATA_SOURCE_KEY']
lecturers_out = []
lecturers_out << ['EXTERNAL_PERSON_KEY','USER_ID','LASTNAME','FIRSTNAME','TITLE','EMAIL','PASSWD','AVAILABLE_IND','ROW_STATUS','DATA_SOURCE_KEY']
lectenrol_out = []
lectenrol_out << ['EXTERNAL_COURSE_KEY','EXTERNAL_PERSON_KEY','ROLE','AVAILABLE_IND','ROW_STATUS','DATA_SOURCE_KEY']

all_lecturers = new HashSet()

itssubjlist.each() { s ->
  sinfo = bb_classrooms[s]
  sinfo.bbcoursemap.each() { bbc,bbcinfo ->
    courses_out << [bbc,bbc,"$sinfo.code: $sinfo.desc $year",bbcinfo.bbtemp,'Y','enabled',DSK_COURSES]
    // keep list of lecturers
    bbcinfo.lecturers.each() { l ->
      all_lecturers.add(l)
      // add enrollment records for lecturer
      lectenrol_out.add([bbc,l,'instructor','Y','enabled',DSK_LECTENROL])
    }
  }
}

println "getting staff details for $all_lecturers"
// now get details for staff members
lectlist = all_lecturers.join(",")
println "...lectlist=$lectlist"
qs = """select PAANUM,PAASUR,PAAFNME,PAATIL,PAAIDN,GETADR1
  from PERSON.PAAPR1,PERSON.PAQSAL,GEN.GETADR
  where PAANUM=GETUNUM
  and PAANUM=PAQNUM and (PAQDTE<SYSDATE and (PAQEDTE is NULL OR PAQEDTE>'01-JAN-2015'))
  and GETADDRTYPE='ET'
  and PAANUM in ($lectlist)
""" as String
println "qs=$qs"

valid_staffids = new HashSet()

db.eachRow(qs) { lr ->
//  passwd = "Dut"+(lr.PAAIDN as String).substring(0,6)
  passwd = "DUT"+lr.PAAIDN
  // no...leave passwd blank to not change
  passwd = ""
  lecturers_out.add([lr.PAANUM,lr.PAANUM,lr.PAASUR,lr.PAAFNME,lr.PAATIL,lr.GETADR1,passwd,'Y','enabled',DSK_STAFF])
  valid_staffids.add(lr.PAANUM)
}

println "writing courses file..."
new File("NEWCOURSES.TXT").withWriter() { out ->
  courses_out.each() { co ->
    out.println co.join("|")
  }
}

println "writing lecturers file..."
new File("NEWLECTURERS.TXT").withWriter() { out ->
  lecturers_out.each() { lo ->
    out.println lo.join("|")
  }
}

def checkStaffNum(sn) {
  if(valid_staffids.contains(sn)) {
    return true
  } else {
    return false
  }
}

println "writing enrolment file..."
new File("NEWENROLS.TXT").withWriter() { out ->
  lectenrol_out.each() { lo ->
    out.println lo.join("|")
  }
}
