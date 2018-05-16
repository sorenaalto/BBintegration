#!/usr/bin/env python
'''
Created on 20 Jun 2016

@author: sorena
'''

#
# program to automate enrollment into classrooms on Blackboard.
# using control spreadsheets + webservices to access subject enrollments
# on ITS

import sys
import xlrd
import requests
import numbers
import datetime
from time import strptime


# constants for YEAR & data source key
#
YEAR = "2017"
DSK_ENROL = "DUT_STUDENROL_"+YEAR
DSK_STUDENTS = "DUT_STUDENTS_"+YEAR

# globals - list of ITS subjcodes, map of ITS -> [bbclassrooms]
#           and map of subj-qual-ot-bc-cgroup -> bbclassrooms
#
itscodes = set()
subj2cr = dict()
enrolmap = dict()
students = set()
enrolout = []
errorlist = []

##############################################################
# processXLS - reads an XLS file & populates the globals
#            with ITS subject & mapping for BB classrooms
#
def processXLS(fname):
    print "processXLS: open spreadsheet file",fname
    wb = xlrd.open_workbook(fname)
    sheet = wb.sheet_by_index(0)
    for rowndx in range(0,sheet.nrows):
        row = sheet.row_values(rowndx)
#        print row
        # check if row has a subject code
        bb_classroom = str(row[0])   # unicode...not need here
        its_scode = str(row[2])
#        print "check",bb_classroom,its_scode
        if len(its_scode)>5 and bb_classroom.startswith(its_scode):
            # check block code...exclude 2nd semester for start of year enrol
            bc=str(row[6])
#            if bc not in ['11','21']:
#                print "processXLS: skipping SEM2",row
            if bc not in ['22']:
                print "processXLS: only for SEM2",row
            else:
                print "processXLS: adding ",its_scode,row
                if not its_scode in itscodes:
                    itscodes.add(its_scode)
                    # create a new set of classrooms
                    subj2cr[its_scode] = set()
                subj2cr[its_scode].add(bb_classroom)
                #
                # now construct the signature string for the enrol mapper
                if isinstance(row[6],numbers.Number):
                    row[6] = "%d" % (row[6])
                mapstr = "%s-%s-%s-%s-%s" % (row[2],row[4],row[6],row[8],row[10])
                enrolmap[mapstr] = row[0]
    print "processXLS: finished processing spreadsheet"

##############################################################
# classlistUrl - REST request URL for classlist for subject
#
def classlistUrl(sc):
    bcode = '22'
    return "http://10.0.100.98:8000/itsenrol/classlist/%s/%s/%s" % (YEAR,bcode,sc)
#    return "http://10.0.100.98:8000/itsenrol/classlist/%s/%s" % (YEAR,sc)

def getClasslist(sc):
    url = classlistUrl(sc)
    print "getWS: requesting: ",url,
    r = requests.get(url)
    print "rsp=",r
    clist = []
    for x in r.json():
        clist.append(x)
    return clist

def getBBClasslist(subj):
    url = "http://10.0.100.98:8000/bbinfo/classlist/%s" % (subj)
    print "getWS: requesting: ",url,
    r = requests.get(url)
    print "rsp=",r
    return r.json()

def getNewStudentsInSubject(sc):
    clist = getClasslist(sc)
    num_its = len(clist)
    #
    # now, get the enrollment from Bb...
    bbcl = getBBClasslist(sc)
    num_bb = len(bbcl)
    # make set of snums in bbcl
    already_enrolled = set()
    for bbs in bbcl:
        snum = bbs['user_id']
        already_enrolled.add(snum)
    #
    # filter the clist to remove the already enrolled students
    newcl = []
    for cls in clist:
        sns = "%d" % cls['IAHSTNO']
        if not sns in already_enrolled:
            newcl.append(cls)
    num_new = len(newcl)
    print "getNewStudentsInSubject: ITS %d, BB %d -> %d new enrollments" % (num_its,num_bb,num_new)
    
    return newcl 


def studentOutput(x):
    snum = x["IAHSTNO"]
#    if x.has_key("GETADR1"):
#        email = x["GETADR1"]
#    else:
#        email = "%s@dut4life.ac.za" % (snum)
 
    # is something not working on ILM?
    email = "%s@dut4life.ac.za" % (snum)
 
    
    # now create initial password out of birthdate?    
    bdate = x["IADBIRDAT"]
    bd = strptime(str(bdate),"%b %d, %Y %H:%M:%S %p")
    pwd = "Dut%02d%02d%02d" % (bd.tm_year % 100,bd.tm_mon,bd.tm_mday)
    linevals = (snum,snum,x["IADSURN"],x["IADNAMES"],x["IADTITLE"],email,pwd,"Y","enabled",DSK_STUDENTS)
    lineout = "%s|%s|%s|%s|%s|%s|%s|%s|%s|%s" % linevals
    return lineout    
    

def enrolOutput(bbc,x):
    if x['IAHCANCELDATE'] != None:
        rowstat = "disabled"
    else:
        rowstat = "enabled"
    linevals = (bbc,x['IAHSTNO'],rowstat,DSK_ENROL)
    lineout =  "%s|%s|student|Y|%s|%s" % linevals
    return lineout
    

##############################################################
# enrolSubjectIntoClassroom - generates enrollment for subject
#        code in the case where scode maps to a single BB
#        classroom
#
def enrolSubjectIntoClassroom(itscode,bbcode):
#    clist = getClasslist(itscode)
    clist = getNewStudentsInSubject(itscode)
    for x in clist:
        try:
            lineout = studentOutput(x)
            students.add(lineout)
            lineout = enrolOutput(bbcode,x)
            enrolout.append(lineout)
        except Exception as ex:
            errorlist.append("enrolSubjectIntoClassroom: Error in x="+str(x))
            print "enrolSubjectIntoClassroom: Error "+str(ex)

##############################################################
# enrolSubjectMapper - generates enrollment for subject
#        code in the case where scode maps to multiple BB
#        classrooms & uses the mapping from the XLS file
#
def enrolSubjectMapper(itscode):
#    clist = getClasslist(itscode)
    clist = getNewStudentsInSubject(itscode)
    for x in clist:
#        print "x=",x
        try:
            mapstr = "%s-%s-%s-%s-%s" % (x['IAHSUBJ'],x['IAHQUAL'],x['IAHBC'],x['IAHOT'],x['IAHCLASSGROUP'])
        except Exception:
            errorlist.append("enrolSubjectMapper: mapping record "+str(x))
            
        lineout = studentOutput(x)
        students.add(lineout)
        if enrolmap.has_key(mapstr):
            bbclass = enrolmap[mapstr]
            lineout = enrolOutput(bbclass,x)
            enrolout.append(lineout)
        else:
            errorlist.append("enrolSubjectMapper: no match "+str(x))
            

def main():
    progname = sys.argv[0]
    xlsfilename = sys.argv[1]
    print progname,": generating enrolment files for subjects in ",xlsfilename
    processXLS(xlsfilename)
#    print "subjectlist=",itscodes
#    print subj2cr
#    print enrolmap

    #
    # now, iterate over the itscodes and get the enrolment for these    
    for sc in itscodes:
        # default BB classroom
        bbcrset = subj2cr[sc]
        if len(bbcrset) == 1:
            # only one classroom for this subject, process as default...
#            enrolSubjectMapper(enrolout,sc)
            bbcr = bbcrset.pop()
            enrolSubjectIntoClassroom(sc,bbcr)
        else:
            print "main: Subject",sc,"maps to multiple classrooms",bbcrset
            enrolSubjectMapper(sc)

    #
    # write the studentfile
    print "main: Writing students..."
    studentfile = open("BBSTUDENTS.TXT","w")
    studentfile.write("EXTERNAL_PERSON_KEY|USER_ID|LASTNAME|FIRSTNAME|TITLE|EMAIL|PASSWD|AVAILABLE_IND|ROW_STATUS|DATA_SOURCE_KEY\n")
    for srec in students:
        # handle funky unicode conversion errors?
        srecout = srec.encode("ascii","replace")
        studentfile.write(srecout+"\n")
    studentfile.close()

    #
    # write the enrolfile
    print "main: Writing enrollments..."
    enrolfile = open("BBENROL.TXT","w")
    enrolfile.write("EXTERNAL_COURSE_KEY|EXTERNAL_PERSON_KEY|ROLE|AVAILABLE_IND|ROW_STATUS|DATA_SOURCE_KEY\n")
    for erec in enrolout:
        enrolfile.write(erec+"\n")
    enrolfile.close()


    print "main: Finished processing",xlsfilename
    print "main:",len(itscodes),"subjects processed"
    print "main:",len(enrolmap),"subject -> classroom maps"
    print "main:",len(students),"students"
    print "main:",len(enrolout),"student enrollments",len(errorlist),"errors"
    if len(errorlist)>0:
        for e in errorlist:
            print "ERROR:",e

    

if __name__ == "__main__":
    main()

