package com.github.unidbg.linux.android.dvm.jni;

import com.github.unidbg.linux.android.dvm.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

class ProxyUtils {

    private static void parseMethodArgs(DvmMethod dvmMethod, List<Class<?>> classes, List<Object> args, VarArg varArg) {
        String shorty = dvmMethod.decodeArgsShorty();
        char[] chars = shorty.toCharArray();
        int offset = 0;
        for (char c : chars) {
            switch (c) {
                case 'B':
                    classes.add(byte.class);
                    args.add((byte) varArg.getInt(offset));
                    offset++;
                    break;
                case 'C':
                    classes.add(char.class);
                    args.add((char) varArg.getInt(offset));
                    offset++;
                    break;
                case 'I':
                    classes.add(int.class);
                    args.add(varArg.getInt(offset));
                    offset++;
                    break;
                case 'S':
                    classes.add(short.class);
                    args.add((short) varArg.getInt(offset));
                    offset++;
                    break;
                case 'Z':
                    classes.add(boolean.class);
                    args.add(varArg.getInt(offset) == VM.JNI_TRUE);
                    offset++;
                    break;
                /*case 'F':
                    args.add(varArg.getFloat(offset));
                    offset++;
                    break;*/
                case 'L':
                    DvmObject<?> dvmObject = varArg.getObject(offset);
                    if (dvmObject == null) {
                        classes.add(null);
                        args.add(null);
                    } else {
                        Object obj = dvmObject.getValue();
                        classes.add(obj.getClass());
                        args.add(obj);
                    }
                    offset++;
                    break;
                /*case 'D':
                    args.add(varArg.getDouble(offset));
                    offset++;
                    break;*/
                /*case 'J':
                    args.add(varArg.getLong(offset));
                    offset++;
                    break;*/
                default:
                    throw new IllegalStateException("c=" + c);
            }
        }
    }

    private static void parseMethodArgs(DvmMethod dvmMethod, List<Class<?>> classes, List<Object> args, VaList vaList) {
        String shorty = dvmMethod.decodeArgsShorty();
        char[] chars = shorty.toCharArray();
        int offset = 0;
        for (char c : chars) {
            switch (c) {
                case 'B':
                    classes.add(byte.class);
                    args.add((byte) vaList.getInt(offset));
                    offset += 4;
                    break;
                case 'C':
                    classes.add(char.class);
                    args.add((char) vaList.getInt(offset));
                    offset += 4;
                    break;
                case 'I':
                    classes.add(int.class);
                    args.add(vaList.getInt(offset));
                    offset += 4;
                    break;
                case 'S':
                    classes.add(short.class);
                    args.add((short) vaList.getInt(offset));
                    offset += 4;
                    break;
                case 'Z':
                    classes.add(boolean.class);
                    args.add(vaList.getInt(offset) == VM.JNI_TRUE);
                    offset += 4;
                    break;
                case 'F':
                    classes.add(float.class);
                    args.add(vaList.getFloat(offset));
                    offset += 4;
                    break;
                case 'L':
                    DvmObject<?> dvmObject = vaList.getObject(offset);
                    if (dvmObject == null) {
                        classes.add(null);
                        args.add(null);
                    } else {
                        Object obj = dvmObject.getValue();
                        classes.add(obj.getClass());
                        args.add(obj);
                    }
                    offset += 4;
                    break;
                case 'D':
                    classes.add(double.class);
                    args.add(vaList.getDouble(offset));
                    offset += 8;
                    break;
                case 'J':
                    classes.add(long.class);
                    args.add(vaList.getLong(offset));
                    offset += 8;
                    break;
                default:
                    throw new IllegalStateException("c=" + c);
            }
        }
    }

    private static boolean matchesTypes(Class<?>[] parameterTypes, Class<?>[] types) {
        if (parameterTypes.length != types.length) {
            return false;
        }
        for (int i = 0; i < types.length; i++) {
            if (types[i] == null) {
                continue;
            }

            if (types[i] != parameterTypes[i]) {
                return false;
            }
        }
        return true;
    }

    private static Method matchMethodTypes(Class<?> clazz, String methodName, Class<?>[] types) throws NoSuchMethodException {
        boolean hasNull = false;
        for (Class<?> cc : types) {
            if (cc == null) {
                hasNull = true;
                break;
            }
        }
        if (!hasNull) {
            return null;
        }

        Set<Method> methods = new HashSet<>();
        for (Method method : clazz.getDeclaredMethods()) {
            if (methodName.equals(method.getName()) && method.getParameterTypes().length == types.length) {
                methods.add(method);
            }
        }
        for (Method method : clazz.getMethods()) {
            if (methodName.equals(method.getName()) && method.getParameterTypes().length == types.length) {
                methods.add(method);
            }
        }
        for (Method method : methods) {
            if (matchesTypes(method.getParameterTypes(), types)) {
                return method;
            }
        }
        throw new NoSuchMethodException(Arrays.toString(types));
    }

    private static Constructor<?> matchConstructorTypes(Class<?> clazz, Class<?>[] types) throws NoSuchMethodException {
        boolean hasNull = false;
        for (Class<?> cc : types) {
            if (cc == null) {
                hasNull = true;
                break;
            }
        }
        if (!hasNull) {
            return null;
        }

        for (Constructor<?> constructor : clazz.getDeclaredConstructors()) {
            if (matchesTypes(constructor.getParameterTypes(), types)) {
                return constructor;
            }
        }
        throw new NoSuchMethodException(Arrays.toString(types));
    }

    static ProxyCall findConstructor(Class<?> clazz, DvmMethod dvmMethod, VarArg varArg) throws NoSuchMethodException {
        if (!"<init>".equals(dvmMethod.getMethodName())) {
            throw new IllegalStateException(dvmMethod.getMethodName());
        }
        List<Class<?>> classes = new ArrayList<>(10);
        List<Object> args = new ArrayList<>(10);
        parseMethodArgs(dvmMethod, classes, args, varArg);
        Class<?>[] types = classes.toArray(new Class<?>[0]);
        Constructor<?> constructor = matchConstructorTypes(clazz, types);
        if (constructor == null) {
            constructor = clazz.getDeclaredConstructor(types);
        }
        return new ProxyConstructor(constructor, args.toArray());
    }

    static ProxyCall findConstructor(Class<?> clazz, DvmMethod dvmMethod, VaList vaList) throws NoSuchMethodException {
        if (!"<init>".equals(dvmMethod.getMethodName())) {
            throw new IllegalStateException(dvmMethod.getMethodName());
        }
        List<Class<?>> classes = new ArrayList<>(10);
        List<Object> args = new ArrayList<>(10);
        parseMethodArgs(dvmMethod, classes, args, vaList);
        Class<?>[] types = classes.toArray(new Class<?>[0]);
        Constructor<?> constructor = matchConstructorTypes(clazz, types);
        if (constructor == null) {
            constructor = clazz.getDeclaredConstructor(types);
        }
        return new ProxyConstructor(constructor, args.toArray());
    }

    static ProxyCall findMethod(Class<?> clazz, DvmMethod dvmMethod, VarArg varArg) throws NoSuchMethodException {
        List<Class<?>> classes = new ArrayList<>(10);
        List<Object> args = new ArrayList<>(10);
        parseMethodArgs(dvmMethod, classes, args, varArg);
        Class<?>[] types = classes.toArray(new Class[0]);
        Method method = matchMethodTypes(clazz, dvmMethod.getMethodName() , types);
        if (method != null) {
            return new ProxyMethod(method, args.toArray());
        }
        try {
            method = clazz.getDeclaredMethod(dvmMethod.getMethodName(), types);
            return new ProxyMethod(method, args.toArray());
        } catch (NoSuchMethodException e) {
            method = clazz.getMethod(dvmMethod.getMethodName(), types);
            return new ProxyMethod(method, args.toArray());
        }
    }

    static ProxyCall findMethod(Class<?> clazz, DvmMethod dvmMethod, VaList vaList) throws NoSuchMethodException {
        List<Class<?>> classes = new ArrayList<>(10);
        List<Object> args = new ArrayList<>(10);
        parseMethodArgs(dvmMethod, classes, args, vaList);
        String methodName = dvmMethod.getMethodName();
        if (clazz == UUID.class && "createString".equals(methodName)) {
            methodName = "toString";
        }
        Class<?>[] types = classes.toArray(new Class[0]);
        Method method = matchMethodTypes(clazz, dvmMethod.getMethodName() , types);
        if (method != null) {
            return new ProxyMethod(method, args.toArray());
        }
        try {
            method = clazz.getDeclaredMethod(methodName, types);
            return new ProxyMethod(method, args.toArray());
        } catch (NoSuchMethodException e) {
            method = clazz.getMethod(methodName, types);
            return new ProxyMethod(method, args.toArray());
        }
    }

    static ProxyField findField(Class<?> clazz, DvmField dvmField) throws NoSuchFieldException {
        String fieldName = dvmField.getFieldName();
        try {
            Field field = clazz.getDeclaredField(fieldName);
            return new ProxyField(field);
        } catch (NoSuchFieldException e) {
            Field field = clazz.getField(fieldName);
            return new ProxyField(field);
        }
    }

}
