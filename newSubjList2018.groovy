#!/usr/bin/env groovy

import groovy.sql.Sql
import org.apache.poi.xssf.usermodel.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.usermodel.*
import org.apache.poi.hssf.usermodel.*;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;

qyear = 2016
nextyear = 2017
hodsurn = args[0]
hodsnum = args[1]
hodlect = "$hodsnum:$hodsurn"
fname = args[2]
dcode = args[3]
dname = args[4]

String toCamelCase( String text) {
	text = text.replace(/&/,"");
	tokens = text.split(/\s+/)
	nt = tokens.collect() { t -> t.toLowerCase().capitalize()}
	return nt.join("")
}

old_course_map = [:]
//f = new File("bb_class_lect.csv")
f = new File("BB2016Class.csv")
f.eachLine() { l ->
	tokens = l.split(",")
	if(tokens.length>9) {
		//println tokens[9]+","+tokens[8]
		old_bb_course = tokens[2]
		if(old_bb_course.length() >= 7) {
			itscode = old_bb_course.substring(0,7)
			if(old_course_map[itscode] == null) {
				old_course_map[itscode] = ['classcode':old_bb_course,'lecturers':new HashSet()]
			}
			lectid = "${tokens[5]}:${tokens[7]}"
			old_course_map[itscode].lecturers.add(lectid)
		}
	}
}

//println "old_course_map=$old_course_map"


/***
f = new File("bb_course_lastmod.csv")
f.eachLine() { l ->
	tokens = l.split(",")
	if(tokens.length>9) {
		//println tokens[9]+","+tokens[8]
		old_bb_course = tokens[9]
		if(old_bb_course.length() >= 7) {
			itscode = old_bb_course.substring(0,7)
			if(old_course_map[itscode] == null) {
				old_course_map[itscode] = old_bb_course
			}
		}
	}
}
***/


bb_bcode = "SEM1"
//data_src_code = "DUTENROL_${bb_bcode}_${qyear}"
data_src_code = "DUTENROL_TEST_2015"

//url = "jdbc:oracle:thin:@neptune.dut.ac.za:1527:PRODI03"
url = "jdbc:oracle:thin:@10.0.4.16:1810:PRODI04"
user = "ilm"
pass = "identity"

db = Sql.newInstance(url,user,pass)



qs = """
select IAHSUBJ,IALSTYPE,IALDESC,IAHQUAL,IAIDESC,
       IAHBC,IIDBCN,IAHOT,GASNAME,
       NVL(IAHCLASSGROUP,'A') as CLASSGRP,
			 count(*) as LASTENROL
from STUD.IAHSUB,STUD.IALSUB,STUD.IAIQAL,GEN.GASOTP,STUD.IIDABL
where
  IAHCYR=IALCYR and IAHSUBJ=IALSUBJ
and IAHCYR=IAICYR and IAHQUAL=IAIQUAL
and IAHOT=GASCODE
and IAHBC=IIDBC
and IALSTYPE!='MM'
and IAHBC not like 'R%'
and IAHBC not like 'E%'
and IAHOT not like 'E%'
and IAHBC in ('11','21','22','P0')
and IALSCHOOLDEPT=$dcode
and IAHCYR=$qyear
group by IAHSUBJ,IALSTYPE,IALDESC,IAHQUAL,IAIDESC,IAHBC,IIDBCN,IAHOT,GASNAME,IAHCLASSGROUP
order by SUBSTR(IAHSUBJ,5,1),IAHSUBJ,IAHBC,IAHQUAL,IAHOT
"""

qs =  """
select IALSUBJ,IALSTYPE,IALDESC,IAKQUAL,IAIDESC,
        IIBBC,IIDBCN,IDDOT,GASNAME,
        IDDCLASSGROUP as CLASSGRP,0 as LASTENROL 
from STUD.IALSUB , STUD.IAKSUB, STUD.IAIQAL, STUD.IIBSBC, GEN.GASOTP, STUD.IDDSCG, STUD.IIDABL
where IALSUBJ=IAKSUBJ and IALCYR=IAKCYR and IIBOT=IAKOT
and IAKQUAL=IAIQUAL and IAKCYR=IAICYR 
and IALSUBJ=IIBSUBJ and IALCYR=IIBCYR
and IDDOT=GASCODE
and IIBBC=IIDBC
and IALSUBJ=IDDSUBJ and IALCYR=IDDCYR  and IDDOT=IIBOT
and IALCYR=$nextyear
and IALSCHOOLDEPT=$dcode
and IALSUBJ in
(select IALSUBJ from STUD.IALSUB group by IALSUBJ having min(IALCYR)=2017)
and IIBBC in ('11','21','22','P0')
and NVL(IALSTYPE,'NS')!='MM'
order by SUBSTR(IALSUBJ,5,1),IALSUBJ,IIBBC,IAKQUAL,IIBOT
"""


rset = []
subjlist = []
bc4subj = [:]
println qs
db.eachRow(qs) { r ->
	rset.add(r.toRowResult())
	subjlist.add(r.IALSUBJ)
	if(bc4subj[r.IALSUBJ] == null) {
		bc4subj[r.IALSUBJ] = [:]
	}
	bcmap = bc4subj[r.IALSUBJ]
	if(bcmap[r.IIBBC] == null) {
		bcmap[r.IIBBC] = r.LASTENROL
	} else {
		bcmap[r.IIBBC] += r.LASTENROL
	}
}

if(subjlist.size()<1) {
	println "No new subjects for this department...bye bye!" ;
	System.exit(0);
}

// now we have to build the map of lecturer assignments
slist = subjlist.collect(){ s -> "'$s'" }.join(",")

qs = """select PAASUR,PAAINT,IDDLECT,IDDSUBJ,IDDCLASSGROUP,IDDOT
	from STUD.IDDSCG,PERSON.PAAPR1,PERSON.PAQSAL
	where IDDLECT=PAANUM
	and PAANUM=PAQNUM
	and (PAQEDTE is null OR PAQEDTE>'01-JAN-2015')
	and IDDCYR=$qyear
	and IDDSUBJ in ($slist)"""

lectinfo = [:]

println qs
db.eachRow(qs as String) { r ->
	scode = r.IDDSUBJ
	cgroup = r.IDDCLASSGROUP
	otype = r.IDDOT
	key = "$scode:$otype:$cgroup" as String
	val = "$r.IDDLECT:$r.PAASUR $r.PAAINT" as String
	if (lectinfo[key] == null) {
		lectinfo[key] = []
	}
	lectinfo[key].add(val)
}

// do the HSSF output
println ",,$dcode,$dname"

Workbook wb = new HSSFWorkbook();
Sheet s = wb.createSheet("BB Classrooms");
CellStyle hdrstyle = wb.createCellStyle();
HSSFFont boldfont = wb.createFont();
boldfont.setBoldweight(HSSFFont.BOLDWEIGHT_BOLD);
hdrstyle.setFont(boldfont)

rndx = 0
Row row = s.createRow(rndx)
Cell ct = row.createCell(0)
ct.setCellStyle(hdrstyle)
ct.setCellValue("2017 NEW SUBJECTS  Blackboard Classrooms for Department: $dcode - $dname")

rndx++
rndx++

//IAKPERSTUDY,IAHSUBJ,IALSTYPE,IALDESC,IAHQUAL,IAIDESC,
//IAHBC,IIDBCN,IAHOT,GASNAME,CLASSGRP,
//ENROL2015,IDDLECT,BB_TEMPLATE,BB_CLASSROOM
cols = ['BB_CLASSROOM','BB_TEMPLATE','IALSUBJ','IALDESC','IAKQUAL','IAIDESC',
				'IIBBC','IIDBCN','IDDOT','GASNAME','CLASSGRP','IDDLECT','LASTENROL']
colhdr = ['BB Classroom','Migrate Classroom','ITS Subject',null,'ITS Qualification',null,
				'Block',null,'Off Type',null,'Grp','Lecturer(s)','2016 Enrol']
colwidth = [20,20,10,50,10,40,3,15,3,20,5,30,6]
colscale = 256
cndx = 0
colwidth.each() { cw ->
	s.setColumnWidth(cndx++,cw*colscale)
}

row = s.createRow(rndx)
cndx = 0
colhdr.each() { k ->
	if(k != null) {
		Cell c = row.createCell(cndx)
		c.setCellStyle(hdrstyle)
		c.setCellValue(k)
	}
	cndx++
}

rndx++

def bbbcode4subj(scode,bcode) {
	bcmap = bc4subj[scode]
	if(bcmap.size()>1) {
		println "BCMAP($scode,$bcode) -> ALL: $bcmap";
		//return "ALL"
	} 

	switch(bcode) {
		case '11' :
		case 'P0' :
			return "YEAR"
		case '21' :
			return "SEM1"
		case '22' :
			return "SEM2"
		default:
			println "BCMAP($scode,$bcode) strange code $bcode";
			return bcode
	}
}

lastbbclass = null
rset.each() { r ->

	key = "$r.IALSUBJ:$r.IDDOT:$r.CLASSGRP"
	if(lectinfo[key] != null) {
		r['IDDLECT'] = lectinfo[key].join(";")
	} else {
		// default to HoD as lecturer
		r['IDDLECT'] = hodlect
	}

	bb_temp = null
	if(old_course_map[r.IALSUBJ] != null) {
		bb_temp = old_course_map[r.IALSUBJ].classcode
		bb_lect_set = old_course_map[r.IALSUBJ].lecturers
		r['IDDLECT'] = bb_lect_set.join(";")
	}
	bn = bbbcode4subj(r.IALSUBJ,r.IIBBC)
	bb_classroom = "${r.IALSUBJ}_${bn}_${nextyear}"

	// skip mother subjects -- should skip entire row
	if(!"MM".equals(r.IALSTYPE)) {
		r['BB_TEMPLATE'] = bb_temp
		r['BB_CLASSROOM'] = bb_classroom
	} else {
		r['BB_TEMPLATE'] = null
		r['BB_CLASSROOM'] = null
	}

	// dump the row...
//	fieldkeys = r.keySet()
//	println fieldkeys.join(",")
	fieldvals = r.values()
	println fieldvals.join(",")

	// leave a blank line if classroom name changes...
	if(lastbbclass == null) {
		lastbbclass = r['BB_CLASSROOM']
	} else {
		bbclass = r['BB_CLASSROOM']
		if(!bbclass.equals(lastbbclass)) {
			rndx++
			lastbbclass = bbclass
		}
	}

	// now create row
	row = s.createRow(rndx)
	cndx = 0
	cols.each() { k ->
		v = r[k]
		Cell c = row.createCell(cndx)
		c.setCellValue(v)
		cndx++
	}

	rndx++
}

ccdname = toCamelCase(dname)
outfilename = "BBCnew${dcode}${ccdname}2017.xls"
println "Saving output file $outfilename..."
FileOutputStream xlsout = new FileOutputStream(outfilename)
wb.write(xlsout)
xlsout.close()

println "Done"
