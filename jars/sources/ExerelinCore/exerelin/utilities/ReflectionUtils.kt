package exerelin.utilities

import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

/**
 * An internal reflection utility designed specifically for Starsector modding.
 *
 * This object provides functions to find and interact with fields, methods, and constructors
 * in potentially obfuscated code. It circumvents classloader restrictions on using `java.lang.reflect`
 * directly by operating exclusively through `java.lang.invoke.MethodHandle`.
 *
 * Key features include:
 * - **Robust Matching:** Functions like `getFieldsMatching`, `getMethodsMatching`, and
 *   `getConstructorsMatching` use sophisticated compatibility checks (including widening,
 *   boxing/unboxing) based on known, non-obfuscated types (e.g., primitives, JDK classes,
 *   Starsector API types) to reliably locate target members even when names or exact types
 *   are obfuscated.
 * - **Simplified Interaction:** Wrapper classes (`ReflectedField`, `ReflectedMethod`,
 *   `ReflectedConstructor`) provide easy get/set/invoke/newInstance capabilities, automatically
 *   handling accessibility (`setAccessible(true)`).
 * - **Convenience API:** Offers both static methods (via `@JvmStatic`) for Java compatibility
 *   and extension functions for idiomatic Kotlin usage.
 * - **Ambiguity Detection:** Functions like `get`, `set`, and `invoke` require exactly one
 *   match to prevent errors when multiple members fit the criteria.
 *
 * ReflectionUtils enables stable interaction with Starsector's obfuscated internals
 * by relying on stable API signatures and Java type system rules rather than obfuscated names
 * which change every release and differ between platforms.
 *
 * @see getFieldsMatching
 * @see getMethodsMatching
 * @see getConstructorsMatching
 * @see ReflectedField
 * @see ReflectedMethod
 * @see ReflectedConstructor
 */

// final version by Starficz
internal object ReflectionUtils {
    private val fieldClass =
        Class.forName("java.lang.reflect.Field", false, Class::class.java.classLoader)
    private val setFieldHandle =
        fieldClass.getHandle("set", Void.TYPE, Any::class.java, Any::class.java)
    private val getFieldHandle =
        fieldClass.getHandle("get", Any::class.java, Any::class.java)
    private val getFieldTypeHandle =
        fieldClass.getHandle("getType", Class::class.java)
    private val getFieldNameHandle =
        fieldClass.getHandle("getName", String::class.java)
    private val setFieldAccessibleHandle =
        fieldClass.getHandle("setAccessible", Void.TYPE, Boolean::class.javaPrimitiveType)

    private val methodClass =
        Class.forName("java.lang.reflect.Method", false, Class::class.java.classLoader)
    private val getMethodNameHandle =
        methodClass.getHandle("getName", String::class.java)
    private val invokeMethodHandle =
        methodClass.getHandle("invoke", Any::class.java, Any::class.java, Array<Any>::class.java)
    private val setMethodAccessibleHandle =
        methodClass.getHandle("setAccessible", Void.TYPE, Boolean::class.javaPrimitiveType)
    private val getMethodReturnHandle =
        methodClass.getHandle("getReturnType", Class::class.java)
    private val getMethodParametersHandle =
        methodClass.getHandle("getParameterTypes", arrayOf<Class<*>>().javaClass)

    private val constructorClass =
        Class.forName("java.lang.reflect.Constructor", false, Class::class.java.classLoader)
    private val getConstructorParametersHandle =
        constructorClass.getHandle("getParameterTypes", arrayOf<Class<*>>().javaClass)
    private val setConstructorAccessibleHandle =
        constructorClass.getHandle("setAccessible", Void.TYPE, Boolean::class.javaPrimitiveType)
    private val invokeConstructorHandle =
        constructorClass.getHandle("newInstance", Any::class.java, arrayOf<Any>()::class.java)


    private fun Class<*>.getHandle(name: String, returnType: Class<*>, vararg paramTypes: Class<*>?): MethodHandle {
        return MethodHandles.lookup().findVirtual(this, name, MethodType.methodType(returnType, paramTypes))
    }

    /**
     * Finds a unique field matching the name and/or type on the provided `instance`'s class (or optionally, superclass's) and returns its value.
     *
     * This method uses [getFieldsMatching] to find fields, specifically checking if the field's type is assignable *to*
     * the provided `type` parameter (i.e., the provided `type` is in interface or superclass of the field's type).
     *
     * This assignable check is mainly useful for getting fields holding obfuscated classes that implement an accessible API.
     * (So that you can pass in the API class to get a field holding the obfuscated implementation of said API)
     *
     * Requires [getFieldsMatching] to find exactly one matching field.
     *
     * @param instance The object instance from which to get the field value.
     * @param name The name of the field (optional).
     * @param type The type that the field's actual type must be assignable *to* (optional).
     *
     * @return The value of the uniquely identified field.
     * @throws IllegalArgumentException if no field matches the criteria or if multiple fields match (ambiguity).
     * @see getFieldsMatching
     */
    @JvmStatic
    @JvmOverloads
    fun get(instance: Any, name: String? = null, type: Class<*>? = null, searchSuperclass: Boolean = false): Any? {
        return instance.get(name, type, searchSuperclass)
    }

    /**
     * Finds a unique field matching the name and/or type on this object's class (or optionally, superclass's) and returns its value.
     *
     * This method uses [getFieldsMatching] to find fields, specifically checking if the field's type is assignable *to*
     * the provided `type` parameter (i.e., the provided `type` is in interface or superclass of the field's type).
     *
     * This assignable check is mainly useful for getting fields holding obfuscated classes that implement an accessible API.
     * (So that you can pass in the API class to get a field holding the obfuscated implementation of said API)
     *
     * Requires [getFieldsMatching] to find exactly one matching field.
     *
     * @receiver The object instance from which to get the field value.
     * @param name The name of the field (optional).
     * @param type The type that the field's actual type must be assignable *from* (optional).
     *
     * @return The value of the uniquely identified field.
     * @throws IllegalArgumentException if no field matches the criteria or if multiple fields match (ambiguity).
     */
    @JvmSynthetic
    @JvmName("ExtensionGet")
    fun Any.get(name: String? = null, type: Class<*>? = null, searchSuperclass: Boolean = false): Any?{
        val reflectedFields = this.getFieldsMatching(name, fieldAssignableTo=type, searchSuperclass=searchSuperclass)
        if (reflectedFields.isEmpty())
            throw IllegalArgumentException("No field found for name: '${name ?: "<any>"}' on class: ${this::class.java.name} " +
                    "that is assignable to type: '${type?.name ?: "<any>"}'.")
        else if (reflectedFields.size > 1)
            throw IllegalArgumentException("Ambiguous fields with name: '${name ?: "<any>"}' on class ${this::class.java.name} " +
                    "assignable to type: '${type?.name ?: "<any>"}'. Multiple fields match.")
        else return reflectedFields[0].get(this)
    }

    /**
     * Finds a unique field matching the type (and name if provided) on the provided `instance`'s class (or optionally, superclass's) and assigns `value` to it.
     *
     * This method uses [getFieldsMatching] to find fields, specifically checking if the type of the provided `value`
     * can be legally assigned *to* the field's actual type.
     *
     * This assignment check is mainly useful for setting fields holding obfuscated classes that implement an accessible API.
     * (So that you can pass in the API class to set a field holding the obfuscated implementation of said API)
     *
     * Requires [getFieldsMatching] to find exactly one matching field.
     *
     * @param instance The object instance from which to get the field value.
     * @param name The name of the field (optional).
     * @param value The value to assign to the uniquely identified field.
     *
     * @throws IllegalArgumentException if no field matches the criteria or if multiple fields match (ambiguity).
     */
    @JvmStatic
    @JvmOverloads
    fun set(instance: Any, name: String? = null, value: Any?, searchSuperclass: Boolean = false) {
        instance.set(name, value, searchSuperclass)
    }

    /**
     * Finds a unique field matching the type (and name if provided) on this object's class (or optionally, superclass's) and assigns `value` to it.
     *
     * This method uses [getFieldsMatching] to find fields, specifically checking if the type of the provided `value`
     * can be legally assigned *to* the field's actual type.
     *
     * This assignment check is mainly useful for setting fields holding obfuscated classes that implement an accessible API.
     * (So that you can pass in the API class to set a field holding the obfuscated implementation of said API)
     *
     * Requires [getFieldsMatching] to find exactly one matching field.
     *
     * @receiver The object instance from which to get the field value.
     * @param name The name of the field (optional).
     * @param value The value to assign to the uniquely identified field.
     *
     * @throws IllegalArgumentException if no field matches the criteria or if multiple fields match (ambiguity).
     */
    @JvmSynthetic
    @JvmName("ExtensionSet")
    fun Any.set(name: String? = null, value: Any?, searchSuperclass: Boolean = false) {
        val valueType = value?.let{ it::class.javaPrimitiveType ?: it::class.java }
        val reflectedFields = this.getFieldsMatching(name, fieldAccepts=valueType, searchSuperclass=searchSuperclass)
        if (reflectedFields.isEmpty())
            throw IllegalArgumentException("No field found for name: '${name ?: "<any>"}' on class: ${this::class.java.name} " +
                    "that accepts type: '${valueType?.name ?: "null"}'.")
        else if (reflectedFields.size > 1)
            throw IllegalArgumentException("Ambiguous fields with name: '${name ?: "<any>"}' on class: ${this::class.java.name} " +
                    "accepting type: '${valueType?.name ?: "null"}'. Multiple fields match.")
        else return reflectedFields[0].set(this, value)
    }

    private fun getAllFields(clazz: Class<*>): Set<Any> {
        var currentClazz: Class<*>? = clazz
        val fields = mutableSetOf<Any>()
        while (currentClazz != Object::class.java && currentClazz != null){
            fields.addAll(currentClazz.declaredFields)
            currentClazz = currentClazz.superclass
        }
        return fields
    }

    /**
     * Finds fields within the class `clazz` (and optionally, its superclass hierarchy) that match the specified criteria.
     *
     * This function searches through fields declared in this class and its superclasses (up to `java.lang.Object`).
     * It considers all fields regardless of their visibility (public, protected, private).
     *
     * Fields are filtered based on the provided optional criteria:
     * - **`name`**: Matches fields with this exact name.
     * - **`type`**: Matches fields declared with this exact `Class` type.
     * - **`fieldAssignableTo` (for GET)**: Matches fields whose type can be assigned *to* the `fieldAssignableTo` type
     *   (i.e., `fieldAssignableTo` is a supertype or interface of the field's type). Useful for finding fields
     *   when you have a variable of a supertype.
     * - **`fieldAccepts` (for SET)**: Matches fields that can accept a value of the `fieldAccepts` type. This check
     *   simulates assignment compatibility, including widening conversions (e.g., `int` to `long`) and
     *   boxing/unboxing conversions between primitives and their wrappers (e.g., `int` to `Integer`).
     *
     * Note: Fields declared as `java.lang.Object` are generally excluded unless explicitly targeted by name,
     * exact type, or an `Object` type in the `fieldAssignableTo` or `fieldAccepts` criteria, to avoid
     * overly broad matches.
     *
     * @param clazz The class in which to search for fields.
     * @param name The exact name the field must have (optional).
     * @param type The exact `Class` type the field must be declared with (optional).
     * @param fieldAssignableTo A type that the field's type must be assignable *to* (optional).
     * @param fieldAccepts A type representing a value that must be assignable *to* the field's type,
     *                     considering standard Java/Kotlin assignment conversions (optional).
     * @return A list of [ReflectedField] objects wrapping the matching fields. Returns an empty list if none match.
     */
    @JvmStatic
    @JvmOverloads
    fun getFieldsMatching(
        clazz: Class<*>,
        name: String? = null,
        type: Class<*>? = null,
        fieldAssignableTo: Class<*>? = null,
        fieldAccepts: Class<*>? = null,
        searchSuperclass: Boolean = false
    ): List<ReflectedField> { return clazz.getFieldsMatching(name, type, fieldAssignableTo, fieldAccepts, searchSuperclass) }

    /**
     * Finds fields within this class (and optionally, its superclass hierarchy) that match the specified criteria.
     *
     * This function searches through fields declared in this class and its superclasses (up to `java.lang.Object`).
     * It considers all fields regardless of their visibility (public, protected, private).
     *
     * Fields are filtered based on the provided optional criteria:
     * - **`name`**: Matches fields with this exact name.
     * - **`type`**: Matches fields declared with this exact `Class` type.
     * - **`fieldAssignableTo` (for GET)**: Matches fields whose type can be assigned *to* the `fieldAssignableTo` type
     *   (i.e., `fieldAssignableTo` is a supertype or interface of the field's type). Useful for finding fields
     *   when you have a variable of a supertype.
     * - **`fieldAccepts` (for SET)**: Matches fields that can accept a value of the `fieldAccepts` type. This check
     *   simulates assignment compatibility, including widening conversions (e.g., `int` to `long`) and
     *   boxing/unboxing conversions between primitives and their wrappers (e.g., `int` to `Integer`).
     *
     * Note: Fields declared as `java.lang.Object` are generally excluded unless explicitly targeted by name,
     * exact type, or an `Object` type in the `fieldAssignableTo` or `fieldAccepts` criteria, to avoid
     * overly broad matches.
     *
     * @receiver The class in which to search for fields.
     * @param name The exact name the field must have (optional).
     * @param type The exact `Class` type the field must be declared with (optional).
     * @param fieldAssignableTo A type that the field's type must be assignable *to* (optional).
     * @param fieldAccepts A type representing a value that must be assignable *to* the field's type,
     *                     considering standard Java/Kotlin assignment conversions (optional).
     * @return A list of [ReflectedField] objects wrapping the matching fields. Returns an empty list if none match.
     */
    @JvmSynthetic
    @JvmName("ExtensionGetFieldsMatching")
    fun Class<*>.getFieldsMatching(
        name: String? = null,
        type: Class<*>? = null,
        fieldAssignableTo: Class<*>? = null,
        fieldAccepts: Class<*>? = null,
        searchSuperclass: Boolean = false
    ): List<ReflectedField> {
        return (if (searchSuperclass) getAllFields(this) else this.declaredFields.toSet()).filter { field ->
            // 1. Check Name
            // Use handle to get name and compare if a specific name is requested.
            if (name != null && name != getFieldNameHandle.invoke(field)) return@filter false

            // --- Field Checks ---
            // Fetch actual field types using the handle *only if needed*.
            if (type != null || fieldAccepts != null || fieldAssignableTo != null) {
                val fieldType = getFieldTypeHandle.invoke(field) as Class<*>
                // 2. Check the exact type
                if (type != null && type != fieldType) return@filter false

                // 3. Check if we can (set with) / (get a) provided type
                if (fieldAccepts != null && !isParameterCompatible(fieldType, fieldAccepts)) return@filter false
                if (fieldAssignableTo != null && !fieldAssignableTo.isAssignableFrom(fieldType)) return@filter false

                // filter out object fields that arnt specifically matched for
                if (fieldType == Object::class.java && // if the found field is object
                    name == null && // and the name wasn't specified
                    // and the user isn't trying to find an object
                    type != Object::class.java &&
                    fieldAssignableTo != Object::class.java &&
                    fieldAccepts != Object::class.java)
                // filter out the field
                    return@filter false
            }
            // If all checks passed up to this point, keep the field
            return@filter true
        }.map { ReflectedField(it) }
    }


    /**
     * Finds fields within `instance` object's class and its superclass hierarchy that match the specified criteria.
     *
     * Instance wrapper around [getFieldsMatching]
     *
     * @param instance The object instance of the class in which to search for fields.
     * @param name The exact name the field must have (optional).
     * @param type The exact `Class` type the field must be declared with (optional).
     * @param fieldAssignableTo A type that the field's type must be assignable *to* (optional).
     * @param fieldAccepts A type representing a value that must be assignable *to* the field's type,
     *                     considering standard Java/Kotlin assignment conversions (optional).
     * @return A list of [ReflectedField] objects wrapping the matching fields. Returns an empty list if none match.
     */
    @JvmStatic
    @JvmOverloads
    fun getFieldsMatching(
        instance: Any,
        name: String? = null,
        type: Class<*>? = null,
        fieldAssignableTo: Class<*>? = null,
        fieldAccepts: Class<*>? = null,
        searchSuperclass: Boolean = false
    ): List<ReflectedField> { return instance::class.java.getFieldsMatching(name, type, fieldAssignableTo, fieldAccepts, searchSuperclass) }

    /**
     * Finds fields within this object's class and its superclass hierarchy that match the specified criteria.
     *
     * Instance wrapper around [getFieldsMatching]
     *
     * @receiver The object instance of the class in which to search for fields.
     * @param name The exact name the field must have (optional).
     * @param type The exact `Class` type the field must be declared with (optional).
     * @param fieldAssignableTo A type that the field's type must be assignable *to* (optional).
     * @param fieldAccepts A type representing a value that must be assignable *to* the field's type,
     *                     considering standard Java/Kotlin assignment conversions (optional).
     * @return A list of [ReflectedField] objects wrapping the matching fields. Returns an empty list if none match.
     */
    @JvmSynthetic
    @JvmName("ExtensionGetFieldsMatching")
    fun Any.getFieldsMatching(
        name: String? = null,
        type: Class<*>? = null,
        fieldAssignableTo: Class<*>? = null,
        fieldAccepts: Class<*>? = null,
        searchSuperclass: Boolean = false
    ): List<ReflectedField> { return this::class.java.getFieldsMatching(name, type, fieldAssignableTo, fieldAccepts, searchSuperclass) }

    /**
     * Finds fields within `clazz`'s class hierarchy whose declared type match the specified criteria.
     *
     * This method uses [getMethodsMatching] to find fields across `clazz`'s class and its superclasses. It identifies fields holding objects
     * whose class possesses at least one method matching the specified criteria. The power comes from using
     * known types for the method signature matching:
     *
     * - **`methodName`**: Matches target methods with exact names, useful if the name is not obfuscated.
     * - **`methodReturnType`**: A known type (`String.class`, API interface) that the target method's
     *   return type must be assignable *to*.
     * - **`numOfMethodParams`**: The exact number of parameters the target method must have.
     * - **`methodParameterTypes`**: An array of known types (`float.class`, `Vector2f.class`, API interface)
     *   representing arguments assignable *to* the target method's parameters (considers widening, boxing/unboxing).
     *
     * This is useful for finding a field when you don't know its name or exact type, but you know it holds
     * an object that has a method with a specific, known signature (e.g., "find the field holding
     * an object that has a method accepting a `String` and returning a `boolean`").
     *
     * @param clazz The class in which to search for fields.
     * @param methodName The exact name of a method that must exist on the field's type (optional).
     * @param methodReturnType A known type that a method's return type on the field's type must be assignable *to* (optional).
     * @param numOfMethodParams The exact number of parameters for a method on the field's type (optional).
     * @param methodParameterTypes An array of known types representing arguments assignable *to*
     *                             the parameters of a method on the field's type (optional).
     * @return A list of [ReflectedField] objects wrapping fields whose type contains at least one matching method.
     */
    @JvmStatic
    @JvmOverloads
    fun getFieldsWithMethodsMatching(
        clazz: Class<*>,
        methodName: String? = null,
        methodReturnType: Class<*>? = null,
        numOfMethodParams: Int? = null,
        methodParameterTypes: Array<Class<*>?>? = null,
        searchSuperclass: Boolean = false
    ): List<ReflectedField> {
        return clazz.getFieldsWithMethodsMatching(methodName, methodReturnType, numOfMethodParams, methodParameterTypes, searchSuperclass)
    }

    /**
     * Finds fields within a class hierarchy whose declared type match the specified criteria.
     *
     * This method uses [getMethodsMatching] to find fields across this class and its superclasses. It identifies fields holding objects
     * whose class possesses at least one method matching the specified criteria. The power comes from using
     * known types for the method signature matching:
     *
     * - **`methodName`**: Matches target methods with exact names, useful if the name is not obfuscated.
     * - **`methodReturnType`**: A known type (`String.class`, API interface) that the target method's
     *   return type must be assignable *to*.
     * - **`numOfMethodParams`**: The exact number of parameters the target method must have.
     * - **`methodParameterTypes`**: An array of known types (`float.class`, `Vector2f.class`, API interface)
     *   representing arguments assignable *to* the target method's parameters (considers widening, boxing/unboxing).
     *
     * This is useful for finding a field when you don't know its name or exact type, but you know it holds
     * an object that has a method with a specific, known signature (e.g., "find the field holding
     * an object that has a method accepting a `String` and returning a `boolean`").
     *
     * @receiver The class in which to search for fields.
     * @param methodName The exact name of a method that must exist on the field's type (optional).
     * @param methodReturnType A known type that a method's return type on the field's type must be assignable *to* (optional).
     * @param numOfMethodParams The exact number of parameters for a method on the field's type (optional).
     * @param methodParameterTypes An array of known types representing arguments assignable *to*
     *                             the parameters of a method on the field's type (optional).
     * @return A list of [ReflectedField] objects wrapping fields whose type contains at least one matching method.
     */
    @JvmSynthetic
    @JvmName("ExtensionGetFieldsWithMethodsMatching")
    fun Class<*>.getFieldsWithMethodsMatching(
        methodName: String? = null,
        methodReturnType: Class<*>? = null,
        numOfMethodParams: Int? = null,
        methodParameterTypes: Array<Class<*>?>? = null,
        searchSuperclass: Boolean = false
    ): List<ReflectedField> {
        return (if (searchSuperclass) getAllFields(this) else this.declaredFields.toSet()).filter { fieldInstance ->
            val fieldType = getFieldTypeHandle.invoke(fieldInstance) as Class<*>?
            fieldType?.getMethodsMatching(methodName, methodReturnType, numOfMethodParams, methodParameterTypes)?.isNotEmpty() == true
        }.map { ReflectedField(it) }
    }

    /**
     * Finds fields within `instance` object's class hierarchy whose declared type match the specified criteria.
     *
     * Instance wrapper around [getFieldsWithMethodsMatching]
     *
     * @param instance The object instance of the class in which to search for fields.
     * @param methodName The exact name of a method that must exist on the field's type (optional).
     * @param methodReturnType A known type that a method's return type on the field's type must be assignable *to* (optional).
     * @param numOfMethodParams The exact number of parameters for a method on the field's type (optional).
     * @param methodParameterTypes An array of known types representing arguments assignable *to*
     *                             the parameters of a method on the field's type (optional).
     * @return A list of [ReflectedField] objects wrapping fields whose type contains at least one matching method.
     */
    @JvmStatic
    @JvmOverloads
    fun getFieldsWithMethodsMatching(
        instance: Any,
        methodName: String? = null,
        methodReturnType: Class<*>? = null,
        numOfMethodParams: Int? = null,
        methodParameterTypes: Array<Class<*>?>? = null,
        searchSuperclass: Boolean = false
    ): List<ReflectedField> {
        return instance::class.java.getFieldsWithMethodsMatching(methodName, methodReturnType, numOfMethodParams, methodParameterTypes, searchSuperclass)
    }

    /**
     * Finds fields within this object's class hierarchy whose declared type match the specified criteria.
     *
     * Instance wrapper around [getFieldsWithMethodsMatching]
     *
     * @receiver The object instance of the class in which to search for fields.
     * @param methodName The exact name of a method that must exist on the field's type (optional).
     * @param methodReturnType A known type that a method's return type on the field's type must be assignable *to* (optional).
     * @param numOfMethodParams The exact number of parameters for a method on the field's type (optional).
     * @param methodParameterTypes An array of known types representing arguments assignable *to*
     *                             the parameters of a method on the field's type (optional).
     * @return A list of [ReflectedField] objects wrapping fields whose type contains at least one matching method.
     */
    @JvmSynthetic
    @JvmName("ExtensionGetFieldsWithMethodsMatching")
    fun Any.getFieldsWithMethodsMatching(
        methodName: String? = null,
        methodReturnType: Class<*>? = null,
        numOfMethodParams: Int? = null,
        methodParameterTypes: Array<Class<*>?>? = null,
        searchSuperclass: Boolean = false
    ): List<ReflectedField> {
        return this::class.java.getFieldsWithMethodsMatching(methodName, methodReturnType, numOfMethodParams, methodParameterTypes, searchSuperclass)
    }


    /**
     * Invokes a method on `instance` using reflection, simplifying calls by automatically matching argument types.
     *
     * This function infers the target method signature based on the runtime types of the provided arguments in `args`.
     * It then uses [getMethodsMatching] to find all the methods matching the given `name` and inferred parameter types.
     *
     * If [getMethodsMatching] return exactly 1 method, it invokes that method, otherwise it throws `IllegalArgumentException`.
     *
     * @param instance The object instance on which to invoke the method.
     * @param name The name of the method to invoke. (optional)
     * @param args The arguments to pass to the method. The runtime types of these arguments are used to find the specific method overload.
     * @return The result returned by the invoked method.
     * @throws IllegalArgumentException if no method is found matching the provided name and inferred argument types
     *                                  according to the rules of [getMethodsMatching] *OR*
     *                                  if multiple methods match the criteria (ambiguity), preventing a safe invocation.
     *                                  The exception message will detail the inferred parameter types causing the ambiguity.
     */
    @JvmStatic
    @JvmOverloads
    fun invoke(instance: Any, name: String? = null, vararg args: Any?): Any? { return instance.invoke(name, *args) }

    /**
     * Invokes a method on this object using reflection, simplifying calls by automatically matching argument types.
     *
     * This function infers the target method signature based on the runtime types of the provided arguments in `args`.
     * It then uses [getMethodsMatching] to find all the methods matching the given `name` and inferred parameter types.
     *
     * If [getMethodsMatching] return exactly 1 method, it invokes that method, otherwise it throws `IllegalArgumentException`.
     *
     * @receiver The object instance on which to invoke the method.
     * @param name The name of the method to invoke. (optional)
     * @param args The arguments to pass to the method. The runtime types of these arguments are used to find the specific method overload.
     * @return The result returned by the invoked method.
     * @throws IllegalArgumentException if no method is found matching the provided name and inferred argument types
     *                                  according to the rules of [getMethodsMatching] *OR*
     *                                  if multiple methods match the criteria (ambiguity), preventing a safe invocation.
     *                                  The exception message will detail the inferred parameter types causing the ambiguity.
     */
    @JvmSynthetic
    @JvmName("ExtensionInvoke")
    fun Any.invoke(name: String? = null, vararg args: Any?): Any? {
        val paramTypes = args.map { arg -> arg?.let{ it::class.javaPrimitiveType ?: it::class.java } }.toTypedArray()
        val reflectedMethods = this.getMethodsMatching(name, parameterTypes=paramTypes)
        if (reflectedMethods.isEmpty())
            throw IllegalArgumentException("No method found for name: '$name' on class: ${this::class.java.name} " +
                    "with compatible parameter types derived from arguments: ${paramTypes.contentToString()}")
        else if (reflectedMethods.size > 1)
            throw IllegalArgumentException("Ambiguous method call for name: '$name' on class: ${this::class.java.name}. " +
                    "Multiple methods match parameter types derived from arguments: ${paramTypes.contentToString()}")
        else return reflectedMethods[0].invoke(this, *args)
    }

    /**
     * Invokes a static method on `clazz`'s class using reflection, simplifying calls by automatically matching argument types.
     *
     * This function infers the target method signature based on the runtime types of the provided arguments in `args`.
     * It then uses [getMethodsMatching] to find all the methods matching the given `name` and inferred parameter types.
     *
     * If [getMethodsMatching] return exactly 1 method, it invokes that method, otherwise it throws `IllegalArgumentException`.
     *
     * @param clazz The class on which to invoke the method.
     * @param name The name of the method to invoke. (optional)
     * @param args The arguments to pass to the method. The runtime types of these arguments are used to find the specific method overload.
     * @return The result returned by the invoked method.
     * @throws IllegalArgumentException if no method is found matching the provided name and inferred argument types
     *                                  according to the rules of [getMethodsMatching] *OR*
     *                                  if multiple methods match the criteria (ambiguity), preventing a safe invocation.
     *                                  The exception message will detail the inferred parameter types causing the ambiguity.
     */
    @JvmStatic
    @JvmOverloads
    fun invoke(clazz: Class<*>, name: String? = null, vararg args: Any?): Any? { return clazz.invoke(name, *args) }

    /**
     * Invokes a static method on this class using reflection, simplifying calls by automatically matching argument types.
     *
     * This function infers the target method signature based on the runtime types of the provided arguments in `args`.
     * It then uses [getMethodsMatching] to find all the methods matching the given `name` and inferred parameter types.
     *
     * If [getMethodsMatching] return exactly 1 method, it invokes that method, otherwise it throws `IllegalArgumentException`.
     *
     * @receiver The class on which to invoke the method.
     * @param name The name of the method to invoke. (optional)
     * @param args The arguments to pass to the method. The runtime types of these arguments are used to find the specific method overload.
     * @return The result returned by the invoked method.
     * @throws IllegalArgumentException if no method is found matching the provided name and inferred argument types
     *                                  according to the rules of [getMethodsMatching] *OR*
     *                                  if multiple methods match the criteria (ambiguity), preventing a safe invocation.
     *                                  The exception message will detail the inferred parameter types causing the ambiguity.
     */
    @JvmSynthetic
    @JvmName("ExtensionInvoke")
    fun Class<*>.invoke(name: String? = null, vararg args: Any?): Any? {
        val paramTypes = args.map { arg -> arg?.let{ it::class.javaPrimitiveType ?: it::class.java } }.toTypedArray()
        val reflectedMethods = this.getMethodsMatching(name, parameterTypes=paramTypes)
        if (reflectedMethods.isEmpty())
            throw IllegalArgumentException("No method found for name '$name' on class ${this::class.java.name} " +
                    "with compatible parameter types derived from arguments: ${paramTypes.contentToString()}")
        else if (reflectedMethods.size > 1)
            throw IllegalArgumentException("Ambiguous method call for name '$name' on class ${this::class.java.name}. " +
                    "Multiple methods match parameter types derived from arguments: ${paramTypes.contentToString()}")
        else return reflectedMethods[0].invoke(null, *args)
    }

    // Map primitives to their corresponding wrapper types
    private val primitiveToWrapper = mapOf<Class<*>, Class<*>>(
        java.lang.Boolean.TYPE to java.lang.Boolean::class.java,
        java.lang.Byte.TYPE to java.lang.Byte::class.java,
        java.lang.Character.TYPE to java.lang.Character::class.java,
        java.lang.Short.TYPE to java.lang.Short::class.java,
        java.lang.Integer.TYPE to java.lang.Integer::class.java,
        java.lang.Long.TYPE to java.lang.Long::class.java,
        java.lang.Float.TYPE to java.lang.Float::class.java,
        java.lang.Double.TYPE to java.lang.Double::class.java,
        java.lang.Void.TYPE to java.lang.Void::class.java
    )

    // Map wrapper types back to their primitives
    private val wrapperToPrimitive = primitiveToWrapper.entries.associate { (k, v) -> v to k }

    // Define which primitives can be widened TO a given primitive key
    // (Key: method parameter type, Value: Set of types that can be passed as arguments)
    private val primitiveWidensFrom = mapOf<Class<*>, Set<Class<*>>>(
        // Primitive Widening the integer types
        java.lang.Short.TYPE to setOf(java.lang.Byte.TYPE),
        java.lang.Integer.TYPE to setOf(java.lang.Byte.TYPE, java.lang.Short.TYPE, java.lang.Character.TYPE),
        java.lang.Long.TYPE to setOf(java.lang.Byte.TYPE, java.lang.Short.TYPE, java.lang.Character.TYPE, java.lang.Integer.TYPE),

        // Primitive Widening the float types
        java.lang.Float.TYPE to setOf(java.lang.Byte.TYPE, java.lang.Short.TYPE, java.lang.Character.TYPE, java.lang.Integer.TYPE, java.lang.Long.TYPE),
        java.lang.Double.TYPE to setOf(java.lang.Byte.TYPE, java.lang.Short.TYPE, java.lang.Character.TYPE, java.lang.Integer.TYPE, java.lang.Long.TYPE, java.lang.Float.TYPE)
    )

    /**
     * Checks if an argument of type `callerArgType` can be passed to a method parameter of type `methodParamType`,
     * considering widening, boxing, and unboxing, all in accordance to JLS 5.3 / 5.5.
     *
     * @param methodParamType The type declared in the method signature.
     * @param callerArgType The type of the argument the caller intends to pass.
     *                      If null, it means the caller specified 'null' in the
     *                      `parameterTypes` array, interpreted as "match any non-primitive".
     * @return True if compatible according to invoke rules, false otherwise.
     */
    private fun isParameterCompatible(methodParamType: Class<*>, callerArgType: Class<*>?): Boolean {
        // Can the method parameter accept a null value? Only if it's not primitive.
        if (callerArgType == null) return !methodParamType.isPrimitive
        // Exact Match
        if (methodParamType == callerArgType) return true

        return when {
            // Case A: Caller provides Reference, Method expects Reference
            // Check: Reference Widening (e.g., Integer -> Number)
            !callerArgType.isPrimitive && !methodParamType.isPrimitive ->
                methodParamType.isAssignableFrom(callerArgType)

            // Case B: Caller provides Primitive, Method expects Primitive
            // Check: Primitive Widening (e.g., int -> long)
            callerArgType.isPrimitive && methodParamType.isPrimitive ->
                primitiveWidensFrom[methodParamType]?.contains(callerArgType) ?: false

            // Case C: Caller provides Primitive, Method expects Reference,
            // Check: Boxing + Reference Widening (e.g., int -> Integer -> Number)
            !methodParamType.isPrimitive && callerArgType.isPrimitive -> {
                val boxedCallerType = primitiveToWrapper[callerArgType]
                boxedCallerType != null && methodParamType.isAssignableFrom(boxedCallerType)
            }

            // Case D: Caller provides Reference, Method expects Primitive
            // Check: Unboxing + Primitive Widening (e.g., Integer -> int -> long)
            !callerArgType.isPrimitive && methodParamType.isPrimitive -> {
                val unboxedCallerType = wrapperToPrimitive[callerArgType]
                if (unboxedCallerType != null)
                    unboxedCallerType == methodParamType || (primitiveWidensFrom[methodParamType]?.contains(unboxedCallerType) ?: false)
                else false
            }

            else -> false // should never happen
        }
    }

    /**
     * Finds methods within `clazz`'s class and its hierarchy that match the specified criteria.
     *
     * Searches declared methods and public inherited methods. Matching relies primarily on
     * known elements of the method signature:
     *
     * - **`name`**: Matches methods with exact names, useful if the name is not obfuscated.
     * - **`returnType`**: Matches methods whose actual (potentially obfuscated) return type is assignable
     *   *to* a known `returnType` (e.g., `String.class`, `float.class`, a known API interface).
     * - **`numOfParams`**: Matches methods with a specific number of parameters.
     * - **`parameterTypes`**: Matches methods where the parameter signature is compatible with the provided
     *   `parameterTypes` array. This is powerful when using known types (e.g., `String.class`,
     *   `Vector2f.class`, API interfaces) within the array. The check simulates invocation compatibility,
     *   including widening and boxing/unboxing, allowing flexibility (e.g., finding a method expecting `Number`
     *   by specifying `Integer.class`). A `null` element matches any non-primitive parameter (useful if you
     *   know a parameter accepts null but not its obfuscated type).
     *
     * This allows finding target methods even when their names and the exact types involved are obfuscated,
     * by relying on the structure and known parts of their signatures.
     *
     * @param clazz The class in which to search for methods.
     * @param name The exact method name (often obfuscated, optional).
     * @param returnType A known type that the method's return type must be assignable *to* (optional).
     * @param numOfParams The exact number of parameters the method must have (optional).
     * @param parameterTypes An array where elements are known types representing arguments
     *                       that must be assignable *to* the method's parameters, considering standard
     *                       invocation conversions (optional). Can use `null` for parameters known to accept null.
     * @return A list of [ReflectedMethod] objects wrapping the matching methods.
     */
    @JvmStatic
    @JvmOverloads
    fun getMethodsMatching(
        clazz: Class<*>,
        name: String? = null,
        returnType: Class<*>? = null, // Caller expects return assignable *to* this type
        numOfParams: Int? = null,
        parameterTypes: Array<Class<*>?>? = null // Types caller *intends to pass* (null element means passing null)
    ): List<ReflectedMethod> { return clazz.getMethodsMatching(name, returnType, numOfParams, parameterTypes)}

    /**
     * Finds methods within this class and its hierarchy that match the specified criteria.
     *
     * Searches declared methods and public inherited methods. Matching relies primarily on
     * known elements of the method signature:
     *
     * - **`name`**: Matches methods with exact names, useful if the name is not obfuscated.
     * - **`returnType`**: Matches methods whose actual (potentially obfuscated) return type is assignable
     *   *to* a known `returnType` (e.g., `String.class`, `float.class`, a known API interface).
     * - **`numOfParams`**: Matches methods with a specific number of parameters.
     * - **`parameterTypes`**: Matches methods where the parameter signature is compatible with the provided
     *   `parameterTypes` array. This is powerful when using known types (e.g., `String.class`,
     *   `Vector2f.class`, API interfaces) within the array. The check simulates invocation compatibility,
     *   including widening and boxing/unboxing, allowing flexibility (e.g., finding a method expecting `Number`
     *   by specifying `Integer.class`). A `null` element matches any non-primitive parameter (useful if you
     *   know a parameter accepts null but not its obfuscated type).
     *
     * This allows finding target methods even when their names and the exact types involved are obfuscated,
     * by relying on the structure and known parts of their signatures.
     *
     * @receiver The class in which to search for methods.
     * @param name The exact method name (often obfuscated, optional).
     * @param returnType A known type that the method's return type must be assignable *to* (optional).
     * @param numOfParams The exact number of parameters the method must have (optional).
     * @param parameterTypes An array where elements are known types representing arguments
     *                       that must be assignable *to* the method's parameters, considering standard
     *                       invocation conversions (optional). Can use `null` for parameters known to accept null.
     * @return A list of [ReflectedMethod] objects wrapping the matching methods.
     */
    @JvmSynthetic
    @JvmName("ExtensionGetMethodsMatching")
    fun Class<*>.getMethodsMatching(
        name: String? = null,
        returnType: Class<*>? = null, // Caller expects return assignable *to* this type
        numOfParams: Int? = null,
        parameterTypes: Array<Class<*>?>? = null // Types caller *intends to pass* (null element means passing null)
    ): List<ReflectedMethod> {
        val accessibleMethods: Set<Any> = (this.declaredMethods + this.methods).toSet()

        return accessibleMethods.filter { method ->
            // 1. Check Name
            // Use handle to get name and compare if a specific name is requested.
            if (name != null && name != getMethodNameHandle.invoke(method)) return@filter false

            // 2. Check Return Type
            if (returnType != null) {
                val actualReturnType = getMethodReturnHandle.invoke(method) as Class<*>
                // Check: Can the method's actual return type be assigned to what the caller expects?
                // (e.g., caller expects Number, method returns Integer -> OK)
                if (!returnType.isAssignableFrom(actualReturnType)) return@filter false
            }

            // --- Parameter Checks ---
            // Fetch actual parameter types using the handle *only if needed*.
            if (numOfParams != null || parameterTypes != null){
                @Suppress("UNCHECKED_CAST")
                val actualParamTypes = getMethodParametersHandle.invoke(method) as Array<Class<*>>

                // 3. Check Number of Parameters
                if (numOfParams != null && numOfParams != actualParamTypes.size) return@filter false

                // 4. Check Specific Parameter Types (using invoke compatibility rules via helper)
                if (parameterTypes != null) {
                    // First make sure that the number of params matches
                    if (parameterTypes.size != actualParamTypes.size) return@filter false

                    // Check each parameter pair for compatibility using the external helper function.
                    parameterTypes.forEachIndexed { index, callerArgType ->
                        if (!isParameterCompatible(actualParamTypes[index], callerArgType)) return@filter false
                    }
                }
            }

            // If all checks passed up to this point, keep the method
            return@filter true
        }.map { method -> ReflectedMethod(method) } // Wrap the matching method objects
    }

    /**
     * Finds methods within `instance` object's class and its hierarchy that match the specified criteria.
     *
     * Instance wrapper around [getMethodsMatching]
     *
     * @param instance An instance of a class in which to search for methods.
     * @param name The exact method name (often obfuscated, optional).
     * @param returnType A known type that the method's return type must be assignable *to* (optional).
     * @param numOfParams The exact number of parameters the method must have (optional).
     * @param parameterTypes An array where elements are known types representing arguments
     *                       that must be assignable *to* the method's parameters, considering standard
     *                       invocation conversions (optional). Can use `null` for parameters known to accept null.
     * @return A list of [ReflectedMethod] objects wrapping the matching methods.
     */
    @JvmStatic
    @JvmOverloads
    fun getMethodsMatching(
        instance: Any,
        name: String? = null,
        returnType: Class<*>? = null, // Caller expects return assignable *to* this type
        numOfParams: Int? = null,
        parameterTypes: Array<Class<*>?>? = null // Types caller *intends to pass* (null element means passing null)
    ): List<ReflectedMethod> { return instance::class.java.getMethodsMatching(name, returnType, numOfParams, parameterTypes)}

    /**
     * Finds methods within this object's class and its hierarchy that match the specified criteria.
     *
     * Instance wrapper around [getMethodsMatching]
     *
     * @receiver The object instance of the class in which to search for methods.
     * @param name The exact method name (often obfuscated, optional).
     * @param returnType A known type that the method's return type must be assignable *to* (optional).
     * @param numOfParams The exact number of parameters the method must have (optional).
     * @param parameterTypes An array where elements are known types representing arguments
     *                       that must be assignable *to* the method's parameters, considering standard
     *                       invocation conversions (optional). Can use `null` for parameters known to accept null.
     * @return A list of [ReflectedMethod] objects wrapping the matching methods.
     */
    @JvmSynthetic
    @JvmName("ExtensionGetMethodsMatching")
    fun Any.getMethodsMatching(
        name: String? = null,
        returnType: Class<*>? = null,
        numOfParams: Int? = null,
        parameterTypes: Array<Class<*>?>? = null
    ): List<ReflectedMethod> { return this::class.java.getMethodsMatching(name, returnType, numOfParams, parameterTypes) }

    /**
     * Finds constructors within `clazz`'s class that match the specified criteria.
     *
     * Searches declared constructors within the receiver `Class`. Matching relies primarily on
     * known elements of the constructor signature:
     *
     * - **`numOfParams`**: Matches constructors with a specific number of parameters.
     * - **`parameterTypes`**: Matches constructors where the parameter signature is compatible with the provided
     *   `parameterTypes` array. This is powerful when using known types (e.g., `String.class`,
     *   `Vector2f.class`, API interfaces) within the array. The check simulates invocation compatibility,
     *   including widening and boxing/unboxing, allowing flexibility (e.g., finding a constructor expecting `Number`
     *   by specifying `Integer.class`). A `null` element matches any non-primitive parameter (useful if you
     *   know a parameter accepts null but not its obfuscated type).
     *
     * This allows finding target constructors even when their names and the exact types involved are obfuscated,
     * by relying on the structure and known parts of their signatures.
     *
     * @param clazz The class in which to search for constructors.
     * @param numOfParams The exact number of parameters the constructor must have (optional).
     * @param parameterTypes An array where elements are known types representing arguments that must be assignable *to*
     *                       the constructor's corresponding parameter types, considering standard Java/Kotlin
     *                       invocation conversions (optional). Can use `null` elements for parameters known to accept null.
     * @return A list of [ReflectedConstructor] objects wrapping the matching constructors. Returns an empty list if none match.
     */
    @JvmStatic
    @JvmOverloads
    fun getConstructorsMatching(
        clazz: Class<*>,
        numOfParams: Int? = null,
        parameterTypes: Array<Class<*>?>? = null
    ): List<ReflectedConstructor> { return clazz.getConstructorsMatching(numOfParams, parameterTypes)}


    /**
     * Finds constructors within this class that match the specified criteria.
     *
     * Searches declared constructors within the receiver `Class`. Matching relies primarily on
     * known elements of the constructor signature:
     *
     * - **`numOfParams`**: Matches constructors with a specific number of parameters.
     * - **`parameterTypes`**: Matches constructors where the parameter signature is compatible with the provided
     *   `parameterTypes` array. This is powerful when using known types (e.g., `String.class`,
     *   `Vector2f.class`, API interfaces) within the array. The check simulates invocation compatibility,
     *   including widening and boxing/unboxing, allowing flexibility (e.g., finding a constructor expecting `Number`
     *   by specifying `Integer.class`). A `null` element matches any non-primitive parameter (useful if you
     *   know a parameter accepts null but not its obfuscated type).
     *
     * This allows finding target constructors even when their names and the exact types involved are obfuscated,
     * by relying on the structure and known parts of their signatures.
     *
     * @receiver The class in which to search for constructors.
     * @param numOfParams The exact number of parameters the constructor must have (optional).
     * @param parameterTypes An array where elements are known types representing arguments that must be assignable *to*
     *                       the constructor's corresponding parameter types, considering standard Java/Kotlin
     *                       invocation conversions (optional). Can use `null` elements for parameters known to accept null.
     * @return A list of [ReflectedConstructor] objects wrapping the matching constructors. Returns an empty list if none match.
     */
    @JvmSynthetic
    @JvmName("ExtensionGetConstructorsMatching")
    fun Class<*>.getConstructorsMatching(
        numOfParams: Int? = null,
        parameterTypes: Array<Class<*>?>? = null
    ): List<ReflectedConstructor> {
        return this.declaredConstructors.filter { constructor ->
            // Get actual parameters
            @Suppress("UNCHECKED_CAST")
            val actualParamTypes = getConstructorParametersHandle.invoke(constructor) as Array<Class<*>>

            // Check num of params
            if (numOfParams != null && numOfParams != actualParamTypes.size) return@filter false

            // Check specific parameter types using isParameterCompatible
            if (parameterTypes != null) {
                // First make sure that the number of params matches
                if (parameterTypes.size != actualParamTypes.size) return@filter false

                // Check each parameter pair for compatibility using the external helper function.
                parameterTypes.forEachIndexed { index, callerArgType ->
                    if (!isParameterCompatible(actualParamTypes[index], callerArgType)) return@filter false
                }
            }
            return@filter true
        }.map { ReflectedConstructor(it) }
    }


    /**
     * Wraps a reflected field (`java.lang.reflect.Field`) discovered within a class.
     *
     * Instances are typically obtained from [getFieldsMatching] or [getFieldsWithMethodsMatching].
     * This class automatically handles making the underlying field accessible via `setAccessible(true)` before performing get/set operations.
     *
     * @property type The declared `Class` type of the wrapped field.
     * @property name The declared name of the wrapped field (which might be obfuscated).
     */
    class ReflectedField(private val field: Any) {
        val type: Class<*> = getFieldTypeHandle.invoke(field) as Class<*>
        val name: String = getFieldNameHandle.invoke(field) as String
        /**
         * Gets the value of the wrapped field from the given object instance.
         * Automatically makes the field accessible before retrieving the value.
         *
         * @param instance The object instance from which to get the field value. Use `null` for static fields.
         * @return The value of the field. Primitive types will be boxed. Returns `null` if the field value is `null`.
         */
        fun get(instance: Any?): Any? {
            setFieldAccessibleHandle.invoke(field, true)
            return getFieldHandle.invoke(field, instance)
        }

        /**
         * Sets the value of the wrapped field on the given object instance.
         * Automatically makes the field accessible before setting the value.
         *
         * @param instance The object instance on which to set the field value. Use `null` for static fields.
         * @param value The new value to assign to the field.
         */
        fun set(instance: Any?, value: Any?) {
            setFieldAccessibleHandle.invoke(field, true)
            setFieldHandle.invoke(field, instance, value)
        }
    }

    /**
     * Wraps a reflected method (`java.lang.reflect.Method`) discovered within a class.
     *
     * Instances are typically obtained from [getMethodsMatching].
     * This class automatically handles making the underlying method accessible via `setAccessible(true)` before invocation.
     *
     * @property returnType The declared return `Class` type of the wrapped method.
     * @property name The declared name of the wrapped method (which might be obfuscated).
     * @property parameterTypes An array containing the declared `Class` types of the method's parameters in order.
     */
    class ReflectedMethod(private val method: Any) {
        @Suppress("UNCHECKED_CAST")
        val parameterTypes: Array<Class<*>> = getMethodParametersHandle.invoke(method) as Array<Class<*>>
        val returnType: Class<*>? = getMethodReturnHandle.invoke(method) as Class<*>?
        val name: String = getMethodNameHandle.invoke(method) as String
        /**
         * Invokes the wrapped method on the given object instance with the specified arguments.
         * Automatically makes the method accessible before invocation.
         *
         * @param instance The object instance on which to invoke the method. Use `null` for static methods.
         * @param arguments The arguments to pass to the method invocation.
         * @return The value returned by the method. Primitive return types will be boxed. Returns `null` if the method
         *         has a `void` return type or explicitly returns `null`.
         */
        fun invoke(instance: Any?, vararg arguments: Any?): Any? {
            setMethodAccessibleHandle.invoke(method, true)
            return invokeMethodHandle.invoke(method, instance, arguments)
        }
    }

    /**
     * Wraps a reflected constructor discovered within a class.
     *
     * Instances are typically obtained from [getConstructorsMatching].
     * This class automatically handles making the underlying constructor accessible before invocation.
     *
     * @property parameterTypes An array containing the declared `Class` types of the constructor's parameters in order.
     */
    class ReflectedConstructor(private val constructor: Any) {
        @Suppress("UNCHECKED_CAST")
        val parameterTypes: Array<Class<*>> = getConstructorParametersHandle.invoke(constructor) as Array<Class<*>>
        /**
         * Creates a new instance by invoking the wrapped constructor with the specified arguments.
         * Automatically makes the constructor accessible before invocation.
         *
         * @param arguments The arguments to pass to the constructor invocation.
         * @return The newly created object instance.
         */
        fun newInstance(vararg arguments: Any?): Any {
            setConstructorAccessibleHandle.invoke(constructor, true)
            return invokeConstructorHandle.invoke(constructor, arguments)
        }
    }
}