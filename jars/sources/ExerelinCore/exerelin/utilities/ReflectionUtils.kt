package exerelin.utilities

import com.fs.starfarer.api.Global
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.net.URL
import java.net.URLClassLoader
import kotlin.reflect.jvm.internal.impl.load.java.structure.JavaClass

// by Lukas22041 from Random Assortment of Things (RAT)
// https://github.com/Lukas22041/Random-Assortment-of-Things/blob/main/src/assortment_of_things/misc/ReflectionUtils.kt
object ReflectionUtils {

    private val fieldClass = Class.forName("java.lang.reflect.Field", false, Class::class.java.classLoader)
    private val setFieldHandle = MethodHandles.lookup().findVirtual(fieldClass, "set", MethodType.methodType(Void.TYPE, Any::class.java, Any::class.java))
    private val getFieldHandle = MethodHandles.lookup().findVirtual(fieldClass, "get", MethodType.methodType(Any::class.java, Any::class.java))
    private val getFieldNameHandle = MethodHandles.lookup().findVirtual(fieldClass, "getName", MethodType.methodType(String::class.java))
    private val setFieldAccessibleHandle = MethodHandles.lookup().findVirtual(fieldClass,"setAccessible", MethodType.methodType(Void.TYPE, Boolean::class.javaPrimitiveType))

    private val methodClass = Class.forName("java.lang.reflect.Method", false, Class::class.java.classLoader)
    private val getMethodNameHandle = MethodHandles.lookup().findVirtual(methodClass, "getName", MethodType.methodType(String::class.java))
    private val invokeMethodHandle = MethodHandles.lookup().findVirtual(methodClass, "invoke", MethodType.methodType(Any::class.java, Any::class.java, Array<Any>::class.java))

    @JvmStatic fun set(fieldName: String, instanceToModify: Any, newValue: Any?)
    {
        var field: Any? = null
        try {  field = instanceToModify.javaClass.getField(fieldName) } catch (e: Throwable) {
            try {  field = instanceToModify.javaClass.getDeclaredField(fieldName) } catch (e: Throwable) { }
        }

        setFieldAccessibleHandle.invoke(field, true)
        setFieldHandle.invoke(field, instanceToModify, newValue)
    }

    @JvmStatic fun get(fieldName: String, instanceToGetFrom: Any): Any? {
        var field: Any? = null
        try {  field = instanceToGetFrom.javaClass.getField(fieldName) } catch (e: Throwable) {
            try {  field = instanceToGetFrom.javaClass.getDeclaredField(fieldName) } catch (e: Throwable) { }
        }

        setFieldAccessibleHandle.invoke(field, true)
        return getFieldHandle.invoke(field, instanceToGetFrom)
    }


    @JvmStatic fun <T> getIncludingSuperclasses(fieldName: String,
                                            instanceToGetFrom: Any,
                                            clazz: Class<T>): Any?
    {
        var field: Any? = null
        try {  field = clazz!!.getField(fieldName) } catch (e: Throwable) {
            try {  field = clazz!!.getDeclaredField(fieldName) } catch (e: Throwable) {}
        }
        if (field == null) {
            if (clazz!!.superclass == null) return null;
            return getIncludingSuperclasses(fieldName, instanceToGetFrom, clazz!!.superclass)
        }

        setFieldAccessibleHandle.invoke(field, true)
        return getFieldHandle.invoke(field, instanceToGetFrom)
    }

    @JvmStatic fun hasMethodOfName(name: String, instance: Any, contains: Boolean = false) : Boolean {
        val instancesOfMethods: Array<out Any> = instance.javaClass.getDeclaredMethods()

        if (!contains) {
            return instancesOfMethods.any { getMethodNameHandle.invoke(it) == name }
        }
        else  {
            return instancesOfMethods.any { (getMethodNameHandle.invoke(it) as String).contains(name) }
        }
    }

    @JvmStatic fun hasVariableOfName(name: String, instance: Any) : Boolean {

        val instancesOfFields: Array<out Any> = instance.javaClass.getDeclaredFields()
        return instancesOfFields.any { getFieldNameHandle.invoke(it) == name }
    }

    @JvmStatic fun instantiate(clazz: Class<*>, vararg arguments: Any?) : Any?
    {
        val args = arguments.map { it!!::class.javaPrimitiveType ?: it!!::class.java }
        val methodType = MethodType.methodType(Void.TYPE, args)

        val constructorHandle = MethodHandles.lookup().findConstructor(clazz, methodType)
        val instance = constructorHandle.invokeWithArguments(arguments.toList())

        return instance
    }

    @JvmStatic fun invoke(methodName: String, instance: Any, vararg arguments: Any?, declared: Boolean = false) : Any?
    {
        var method: Any? = null

        val clazz = instance.javaClass
        val args = arguments.map { it!!::class.javaPrimitiveType ?: it::class.java }
        val methodType = MethodType.methodType(Void.TYPE, args)

        if (!declared) {
            method = clazz.getMethod(methodName, *methodType.parameterArray())
        }
        else  {
            method = clazz.getDeclaredMethod(methodName, *methodType.parameterArray())
        }

        return invokeMethodHandle.invoke(method, instance, arguments)
    }

    @JvmStatic fun <T> invokeIncludingSuperclasses(methodName: String, instance: Any, clazz: Class<T>, vararg arguments: Any?, declared: Boolean = false) : Any?
    {
        var method: Any? = null

        val args = arguments.map { it!!::class.javaPrimitiveType ?: it::class.java }
        val methodType = MethodType.methodType(Void.TYPE, args)

        try {
            if (!declared) {
                method = clazz.getMethod(methodName, *methodType.parameterArray())
            } else {
                method = clazz.getDeclaredMethod(methodName, *methodType.parameterArray())
            }
        } catch (ex: NoSuchMethodException) {}

        if (method == null) {
            if (clazz!!.superclass == null) return null;
            return invokeIncludingSuperclasses(methodName, instance, clazz!!.superclass, arguments, declared)
        }

        return invokeMethodHandle.invoke(method, instance, arguments)
    }

    @JvmStatic fun getField(fieldName: String, instanceToGetFrom: Any) : ReflectedField? {
        var field: Any? = null
        try {  field = instanceToGetFrom.javaClass.getField(fieldName) } catch (e: Throwable) {
            try {  field = instanceToGetFrom.javaClass.getDeclaredField(fieldName) } catch (e: Throwable) { }
        }

        if (field == null) return null

        return ReflectedField(field)
    }

    @JvmStatic fun getMethod(methodName: String, instance: Any, vararg arguments: Any?) : ReflectedMethod? {
        var method: Any? = null

        val clazz = instance.javaClass
        val args = arguments.map { it!!::class.javaPrimitiveType ?: it::class.java }
        val methodType = MethodType.methodType(Void.TYPE, args)

        try { method = clazz.getMethod(methodName, *methodType.parameterArray())  }
        catch (e: Throwable) {
            try {  method = clazz.getDeclaredMethod(methodName, *methodType.parameterArray()) } catch (e: Throwable) { }
        }

        if (method == null) return null
        return ReflectedMethod(method)
    }

    @JvmStatic fun createClassThroughCustomLoader(claz: Class<*>) : MethodHandle
    {
        var loader = this::class.java.classLoader
        val urls: Array<URL> = (loader as URLClassLoader).urLs
        val reflectionLoader: Class<*> = object : URLClassLoader(urls, ClassLoader.getSystemClassLoader()) {
        }.loadClass(claz.name)
        var handle = MethodHandles.lookup().findConstructor(reflectionLoader, MethodType.methodType(Void.TYPE))
        return handle
    }

    class ReflectedField(private val field: Any) {
        fun get(): Any? = getFieldHandle.invoke(field)
        fun set(instance: Any?, value: Any?) {
            setFieldHandle.invoke(field, instance, value)
        }
    }

    class ReflectedMethod(private val method: Any) {
        fun invoke(instance: Any?, vararg arguments: Any?): Any? = invokeMethodHandle.invoke(method, instance, arguments)
    }
}