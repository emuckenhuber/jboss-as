#!/bin/sh

DIRNAME=`dirname "$0"`

# OS specific support (must be 'true' or 'false').
cygwin=false;
darwin=false;
linux=false;
case "`uname`" in
    CYGWIN*)
        cygwin=true
        ;;

    Darwin*)
        darwin=true
        ;;

    Linux)
        linux=true
        ;;
esac

# For Cygwin, ensure paths are in UNIX format before anything is touched
if $cygwin ; then
    [ -n "$JBOSS_HOME" ] &&
        JBOSS_HOME=`cygpath --unix "$JBOSS_HOME"`
    [ -n "$JAVA_HOME" ] &&
        JAVA_HOME=`cygpath --unix "$JAVA_HOME"`
    [ -n "$JAVAC_JAR" ] &&
        JAVAC_JAR=`cygpath --unix "$JAVAC_JAR"`
fi

# Setup JBOSS_HOME
if [ "x$JBOSS_HOME" = "x" ]; then
    # get the full path (without any relative bits)
    JBOSS_HOME=`cd "$DIRNAME/.."; pwd`
fi
export JBOSS_HOME

# Setup the JVM
if [ "x$JAVA" = "x" ]; then
    if [ "x$JAVA_HOME" != "x" ]; then
        JAVA="$JAVA_HOME/bin/java"
    else
        JAVA="java"
    fi
fi

# For Cygwin, switch paths to Windows format before running java
if $cygwin; then
    JBOSS_HOME=`cygpath --path --windows "$JBOSS_HOME"`
    JAVA_HOME=`cygpath --path --windows "$JAVA_HOME"`
fi

PATCHES=""
if [ -f $JBOSS_HOME/patches/.metadata/cumulative ]; then
    REF=`head -n 1 $JBOSS_HOME/patches/.metadata/cumulative`;
    if [ -f $JBOSS_HOME/patches/.metadata/references/$REF ]; then
        for PATCH in `cat $JBOSS_HOME/patches/.metadata/references/$REF`; do
            if [ "x$PATCHES" = "x" ]; then
                PATCHES=$JBOSS_HOME/patches/$PATCH
            else
                PATCHES=$PATCHES:$JBOSS_HOME/patches/$PATCH
            fi
        done
    fi
    if [ -d $JBOSS_HOME/patches/$REF]; then
        if [ "x$PATCHES" = "x" ]; then
            PATCHES=$JBOSS_HOME/patches/$REF
        else
            PATCHES=$PATCHES:$JBOSS_HOME/patches/$REF
        fi
    fi
fi

if [ "x$MODULEPATH" = "x" ]; then
    MODULEPATH="$JBOSS_HOME/modules"
fi

if [ "x$PATCHES" != "x" ]; then
    MODULEPATH="$PATCHES:$MODULEPATH"
fi

# Sample JPDA settings for remote socket debugging
#JAVA_OPTS="$JAVA_OPTS -Xrunjdwp:transport=dt_socket,address=8787,server=y,suspend=n"

eval \"$JAVA\" $JAVA_OPTS -jar \"$JBOSS_HOME/jboss-modules.jar\" -logmodule "org.jboss.logmanager" -mp \"$MODULEPATH\" org.jboss.as.cli '"$@"'
