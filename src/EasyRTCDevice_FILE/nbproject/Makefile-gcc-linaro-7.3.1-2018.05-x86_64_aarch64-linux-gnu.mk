#
# Generated Makefile - do not edit!
#
# Edit the Makefile in the project folder instead (../Makefile). Each target
# has a -pre and a -post target defined where you can add customized code.
#
# This makefile implements configuration specific macros and targets.


# Environment
MKDIR=mkdir
CP=cp
GREP=grep
NM=nm
CCADMIN=CCadmin
RANLIB=ranlib
CC=aarch64-linux-gnu-gcc
CCC=aarch64-linux-gnu-g++
CXX=aarch64-linux-gnu-g++
FC=gfortran
AS=as

# Macros
CND_PLATFORM=gcc-linaro-7.3.1-Linux
CND_DLIB_EXT=so
CND_CONF=gcc-linaro-7.3.1-2018.05-x86_64_aarch64-linux-gnu
CND_DISTDIR=dist
CND_BUILDDIR=build

# Include project Makefile
include Makefile

# Object Directory
OBJECTDIR=${CND_BUILDDIR}/${CND_CONF}/${CND_PLATFORM}

# Object Files
OBJECTFILES= \
	${OBJECTDIR}/FileParser/ESFileParser.o \
	${OBJECTDIR}/FileParser/ESParser.o \
	${OBJECTDIR}/FileParser/buff.o \
	${OBJECTDIR}/FileParser/g711.o \
	${OBJECTDIR}/FileParser/getsps.o \
	${OBJECTDIR}/FileParser/h265_sei.o \
	${OBJECTDIR}/FileParser/h265_stream.o \
	${OBJECTDIR}/gettimeofdayEx.o \
	${OBJECTDIR}/main_GBS.o


# C Compiler Flags
CFLAGS=

# CC Compiler Flags
CCFLAGS=
CXXFLAGS=

# Fortran Compiler Flags
FFLAGS=

# Assembler Flags
ASFLAGS=

# Link Libraries and Options
LDLIBSOPTIONS=-L../../linux/gcc-linaro-7.3.1-2018.05-x86_64_aarch64-linux-gnu -L../GBSDevice/dist/gcc-linaro-7.3.1-2018.05-x86_64_aarch64-linux-gnu/gcc-linaro-7.3.1-Linux -lEasyRTCDevice -lEasyRTC

# Build Targets
.build-conf: ${BUILD_SUBPROJECTS}
	"${MAKE}"  -f nbproject/Makefile-${CND_CONF}.mk ${CND_DISTDIR}/${CND_CONF}/${CND_PLATFORM}/easyrtcdevice_file

${CND_DISTDIR}/${CND_CONF}/${CND_PLATFORM}/easyrtcdevice_file: ${OBJECTFILES}
	${MKDIR} -p ${CND_DISTDIR}/${CND_CONF}/${CND_PLATFORM}
	${LINK.cc} -o ${CND_DISTDIR}/${CND_CONF}/${CND_PLATFORM}/easyrtcdevice_file ${OBJECTFILES} ${LDLIBSOPTIONS} -lpthread

${OBJECTDIR}/FileParser/ESFileParser.o: FileParser/ESFileParser.cpp
	${MKDIR} -p ${OBJECTDIR}/FileParser
	${RM} "$@.d"
	$(COMPILE.cc) -g -I. -MMD -MP -MF "$@.d" -o ${OBJECTDIR}/FileParser/ESFileParser.o FileParser/ESFileParser.cpp

${OBJECTDIR}/FileParser/ESParser.o: FileParser/ESParser.cpp
	${MKDIR} -p ${OBJECTDIR}/FileParser
	${RM} "$@.d"
	$(COMPILE.cc) -g -I. -MMD -MP -MF "$@.d" -o ${OBJECTDIR}/FileParser/ESParser.o FileParser/ESParser.cpp

${OBJECTDIR}/FileParser/buff.o: FileParser/buff.cpp
	${MKDIR} -p ${OBJECTDIR}/FileParser
	${RM} "$@.d"
	$(COMPILE.cc) -g -I. -MMD -MP -MF "$@.d" -o ${OBJECTDIR}/FileParser/buff.o FileParser/buff.cpp

${OBJECTDIR}/FileParser/g711.o: FileParser/g711.cpp
	${MKDIR} -p ${OBJECTDIR}/FileParser
	${RM} "$@.d"
	$(COMPILE.cc) -g -I. -MMD -MP -MF "$@.d" -o ${OBJECTDIR}/FileParser/g711.o FileParser/g711.cpp

${OBJECTDIR}/FileParser/getsps.o: FileParser/getsps.cpp
	${MKDIR} -p ${OBJECTDIR}/FileParser
	${RM} "$@.d"
	$(COMPILE.cc) -g -I. -MMD -MP -MF "$@.d" -o ${OBJECTDIR}/FileParser/getsps.o FileParser/getsps.cpp

${OBJECTDIR}/FileParser/h265_sei.o: FileParser/h265_sei.cpp
	${MKDIR} -p ${OBJECTDIR}/FileParser
	${RM} "$@.d"
	$(COMPILE.cc) -g -I. -MMD -MP -MF "$@.d" -o ${OBJECTDIR}/FileParser/h265_sei.o FileParser/h265_sei.cpp

${OBJECTDIR}/FileParser/h265_stream.o: FileParser/h265_stream.cpp
	${MKDIR} -p ${OBJECTDIR}/FileParser
	${RM} "$@.d"
	$(COMPILE.cc) -g -I. -MMD -MP -MF "$@.d" -o ${OBJECTDIR}/FileParser/h265_stream.o FileParser/h265_stream.cpp

${OBJECTDIR}/gettimeofdayEx.o: gettimeofdayEx.cpp
	${MKDIR} -p ${OBJECTDIR}
	${RM} "$@.d"
	$(COMPILE.cc) -g -I. -MMD -MP -MF "$@.d" -o ${OBJECTDIR}/gettimeofdayEx.o gettimeofdayEx.cpp

${OBJECTDIR}/main_GBS.o: main_GBS.cpp
	${MKDIR} -p ${OBJECTDIR}
	${RM} "$@.d"
	$(COMPILE.cc) -g -I. -MMD -MP -MF "$@.d" -o ${OBJECTDIR}/main_GBS.o main_GBS.cpp

# Subprojects
.build-subprojects:

# Clean Targets
.clean-conf: ${CLEAN_SUBPROJECTS}
	${RM} -r ${CND_BUILDDIR}/${CND_CONF}

# Subprojects
.clean-subprojects:

# Enable dependency checking
.dep.inc: .depcheck-impl

include .dep.inc
