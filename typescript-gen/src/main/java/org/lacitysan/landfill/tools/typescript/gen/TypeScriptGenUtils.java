package org.lacitysan.landfill.tools.typescript.gen;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.lacitysan.landfill.lib.utils.StringUtils;
import org.lacitysan.landfill.lib.utils.StringUtils.Capitalization;
import org.lacitysan.landfill.tools.typescript.gen.constants.Type;
import org.lacitysan.landfill.tools.typescript.gen.model.constant.TypeScriptEnumConstant;
import org.lacitysan.landfill.tools.typescript.gen.model.field.TypeScirptObjectField;
import org.lacitysan.landfill.tools.typescript.gen.model.field.TypeScriptArrayField;
import org.lacitysan.landfill.tools.typescript.gen.model.field.TypeScriptEnumField;
import org.lacitysan.landfill.tools.typescript.gen.model.field.TypeScriptField;
import org.lacitysan.landfill.tools.typescript.gen.model.field.TypeScriptSimpleField;
import org.lacitysan.landfill.tools.typescript.gen.model.type.TypeScriptClass;
import org.lacitysan.landfill.tools.typescript.gen.model.type.TypeScriptEnum;
import org.lacitysan.landfill.tools.typescript.gen.model.type.TypeScriptSimpleType;
import org.lacitysan.landfill.tools.typescript.gen.model.type.TypeScriptType;
import org.springframework.beans.BeanUtils;

/**
 * @author Alvin Quach
 */
public class TypeScriptGenUtils {
	
	public static TypeScriptClass processClass(Class<?> clazz, Collection<TypeScriptClass> generatedClasses) {
		TypeScriptType search = generatedClasses.stream().filter(g -> g.getClazz() == clazz).findFirst().orElse(null);
		if (search != null) {
			return (TypeScriptClass)search;
		}
		System.out.println("Processing class: " + clazz.getName());
		TypeScriptClass result = new TypeScriptClass(clazz);
		generatedClasses.add(result);
		for (Field field : clazz.getDeclaredFields()) {
			if (!containsGetter(field, clazz.getMethods()) || !containsSetter(field, clazz.getMethods())) {
				continue;
			}
			Type fieldType = TypeScriptGenUtils.getType(field.getType());
			if (fieldType == Type.BOOLEAN || fieldType == Type.NUMBER || fieldType == Type.STRING) {
				result.getFields().add(new TypeScriptSimpleField(field.getName(), fieldType));
			}
			else if (fieldType == Type.ARRAY) {
				TypeScriptArrayField arrayField = new TypeScriptArrayField(field.getName());
				Class<?> genericClass = (Class<?>)((ParameterizedType)field.getGenericType()).getActualTypeArguments()[0];
				Type genericType = getType(genericClass);
				if (genericType == Type.OBJECT) {
					TypeScriptClass proccessedClass = processClass(genericClass, generatedClasses);
					result.getDependencies().add(proccessedClass);
					arrayField.setGenericType(proccessedClass);
				}
				else if (genericType == Type.ENUM) {
					TypeScriptEnum proccessedEnum = processEnum(genericClass, generatedClasses);
					result.getDependencies().add(proccessedEnum);
					arrayField.setGenericType(proccessedEnum);
				}
				else {
					arrayField.setGenericType(new TypeScriptSimpleType(genericType));
				}
				result.getFields().add(arrayField);
			}
			else if (fieldType == Type.ENUM) {
				TypeScriptEnum proccessedEnum = processEnum(field.getType(), generatedClasses);
				result.getDependencies().add(proccessedEnum);
				result.getFields().add(new TypeScriptEnumField(field.getName(), proccessedEnum));
			}
			else if (fieldType == Type.OBJECT) {
				TypeScriptClass proccessedClass = processClass(field.getType(), generatedClasses);
				result.getDependencies().add(proccessedClass);
				result.getFields().add(new TypeScirptObjectField(field.getName(), proccessedClass));
			}
		}
		return result;
	}

	public static TypeScriptEnum processEnum(Class<?> clazz, Collection<TypeScriptClass> generatedClasses) {
		TypeScriptType search = generatedClasses.stream().filter(g -> g.getClazz() == clazz).findFirst().orElse(null);
		if (search != null) {
			return (TypeScriptEnum)search;
		}
		System.out.println("Processing enum: " + clazz.getName());
		TypeScriptEnum result = new TypeScriptEnum(clazz);
		generatedClasses.add(result);
		Map<String, Method> properties = new HashMap<>();
		for (Field field : clazz.getDeclaredFields()) {
			Method getter = findGetter(field, clazz.getMethods());
			if (getter == null) {
				continue;
			}
			Type fieldType = getType(field.getType());
			if (fieldType == Type.BOOLEAN || fieldType == Type.NUMBER || fieldType == Type.STRING) {
				result.getFields().add(new TypeScriptSimpleField(field.getName(), fieldType, true));
			}
			else if (fieldType == Type.ENUM) {
				TypeScriptEnum proccessedEnum = processEnum(field.getType(), generatedClasses);
				result.getDependencies().add(proccessedEnum);
				result.getFields().add(new TypeScriptEnumField(field.getName(), proccessedEnum, true));
			}
			else {
				continue;
			}
			properties.put(field.getName(), getter);
		}
		for (Object enumConstant : clazz.getEnumConstants()) {
			TypeScriptEnumConstant constant = new TypeScriptEnumConstant();
			constant.setName(enumConstant.toString());
			constant.setOrdinal(result.getConstants().size());
			for (String propertyName : properties.keySet()) {
				try {
					Object property = properties.get(propertyName).invoke(enumConstant);
					constant.getProperties().put(propertyName, property);
				} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
					e.printStackTrace();
				}
			}
			result.getConstants().add(constant);
		}
		return result;
	}

	/** 
	 * Determines the <code>Type</code> of a given class.
	 * @param clazz
	 * @return
	 */
	public static Type getType(Class<?> clazz) {

		// Enums
		if (clazz.isEnum()) {
			return Type.ENUM;
		}

		// Simple fields
		else if (BeanUtils.isSimpleValueType(clazz)) {

			// Strings and Characters
			if (clazz == String.class || clazz == Character.class || clazz.equals(Character.TYPE)) {
				return Type.STRING;
			}

			// Boolean
			else if (clazz == Boolean.class || clazz.equals(Boolean.TYPE)) {
				return Type.BOOLEAN;
			}

			// Numbers
			else if (Number.class.isAssignableFrom(clazz) || clazz.isPrimitive()) {
				return Type.NUMBER;
			}

			// Date / Time
			else if (Date.class.isAssignableFrom(clazz)) {
				return Type.NUMBER;
			}

			// Other simple classes
			else {
				return Type.STRING;
			}

		}

		// Arrays and Collections
		else if (clazz.isArray() || Collection.class.isAssignableFrom(clazz)) {
			return Type.ARRAY;
		}

		// Maps
		else if (Map.class.isAssignableFrom(clazz)) {
			// Currently not supported
		}

		// Objects. Will only consider explicitly declared classes and classes in the base package, both of which are defined in TypeScriptGenConfig.
		else if (baseClassesContains(clazz) || clazz.getName().startsWith(TypeScriptGenConfig.BASE_PACKAGE)){
			return Type.OBJECT;
		}

		return null;

	}
	
	private static String generateImportHeader(Class<?> clazz, Set<TypeScriptClass> dependencies) {
		if (dependencies.isEmpty()) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		for (TypeScriptClass dependency : dependencies) {
			sb.append("import {")
			.append(TypeScriptGenConfig.ADD_SPACES_TO_IMPORT ? " " : "")
			.append(dependency.getClazz().getSimpleName())
			.append(TypeScriptGenConfig.ADD_SPACES_TO_IMPORT ? " " : "")
			.append("} from '")
			.append(TypeScriptGenUtils.getRelativePath(clazz, dependency.getClazz()))
			.append(TypeScriptGenUtils.getFilename(dependency))
			.append("';\n");
		}
		sb.append("\n");
		return sb.toString();
	}

	private static String generateClassComments(TypeScriptClass generatedClass) {
		if (TypeScriptGenConfig.CLASS_COMMENT.length == 0) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		sb.append("/**\n");
		for (String commentLine : TypeScriptGenConfig.CLASS_COMMENT) {
			sb.append(" * ");
			sb.append(commentLine
					.replaceAll("%type", generatedClass.getType() == Type.ENUM ? "enum" : "class")
					.replaceAll("%classname", generatedClass.getClazz().getSimpleName())
					.replaceAll("%filename", TypeScriptGenUtils.getFilename(generatedClass)));
			sb.append("\n");
		}
		sb.append(" */\n");
		return sb.toString();
	}

	private static String generateExportDeclaration(TypeScriptClass generatedClass) {
		return "export class " 
				+ generatedClass.getClazz().getSimpleName() 
				+ " {\n";
	}

	private static String generateFields(TypeScriptClass generatedClass) {
		StringBuilder sb = new StringBuilder();
		for (TypeScriptField field : generatedClass.getFields()) {
			sb.append("\t")
			.append(field.isReadonly() ? "readonly " : "")
			.append(field.getFieldName())
			.append(":");
			if (field instanceof TypeScirptObjectField) {
				sb.append(((TypeScirptObjectField)field).getClassType().getClazz().getSimpleName());
			}
			else if (field instanceof TypeScriptEnumField) {
				sb.append(((TypeScriptEnumField)field).getEnumType().getClazz().getSimpleName());
			}
			else if (field instanceof TypeScriptArrayField) {
				TypeScriptType genericType = ((TypeScriptArrayField)field).getGenericType();
				if (genericType.getType() == Type.OBJECT || genericType.getType() == Type.ENUM) {
					sb.append(genericType.getClazz().getSimpleName());
				}
				else {
					sb.append(genericType.getType().name().toLowerCase());
				}
				sb.append("[]");
			}
			else {
				sb.append(field.getFieldType().name().toLowerCase());
			}
			sb.append(";\n");
		}
		return sb.toString();
	}
	
	private static String generateEnumConstants(TypeScriptEnum generatedClass) {
		StringBuilder sb = new StringBuilder();
		for (TypeScriptEnumConstant constant : generatedClass.getConstants()) {
			sb.append("\tstatic readonly ")
			.append(constant.getName())
			.append(":")
			.append(generatedClass.getClazz().getSimpleName())
			.append(" = new ")
			.append(generatedClass.getClazz().getSimpleName())
			.append("(")
			.append(constant.getOrdinal());
			generatedClass.getFields().stream().map(field -> field.getFieldName()).forEach(propertyName -> {
				Object propertyValue = constant.getProperties().get(propertyName);
				if (propertyValue == null) {
					sb.append(", null");					
					return;
				}
				Type propertyType = TypeScriptGenUtils.getType(propertyValue.getClass());
				if (propertyType == Type.STRING) {
					sb.append(", ")
					.append("\"")
					.append(propertyValue)
					.append("\"");
				}
				else if (propertyType == Type.BOOLEAN || propertyType == Type.NUMBER) {
					sb.append(", ")
					.append(propertyValue);
				}
				else if (propertyType == Type.ENUM) {
					sb.append(", ")
					.append(propertyValue.getClass().getSimpleName())
					.append(".")
					.append(((Enum)propertyValue).name());
				}
			});
			sb.append(");\n");
		}
		sb.append("\n");
		return sb.toString();
	}
	
	public static String generateClass(TypeScriptClass generatedClass) {

		StringBuilder sb = new StringBuilder();

		// Imported dependencies declarations.
		sb.append(generateImportHeader(generatedClass.getClazz(), generatedClass.getDependencies()))

		// Auto-generated class comments.
		.append(generateClassComments(generatedClass))

		// Export class declaration.
		.append(generateExportDeclaration(generatedClass))

		// Fields
		.append(generateFields(generatedClass))

		// Closing bracket
		.append("}");

		return sb.toString();
	}

	public static String generateEnum(TypeScriptEnum generatedClass) {

		StringBuilder sb = new StringBuilder();

		// Imported dependencies declarations.
		sb.append(generateImportHeader(generatedClass.getClazz(), generatedClass.getDependencies()))

		// Auto-generated class comments.
		.append(generateClassComments(generatedClass))

		// Export class declaration.
		.append(generateExportDeclaration(generatedClass))
		.append("\n")

		// Enum constants
		.append(generateEnumConstants(generatedClass));

		// Enum property fields
		String fields = generateFields(generatedClass);
		sb.append("\treadonly ordinal:number;\n")
		.append(fields)
		.append("\n");
		
		// Constructor
		sb.append("\tprivate constructor(ordinal:number");
		for (String param : fields.replaceAll("\t", "").replaceAll("readonly ", "").split(";\n")) {
			sb.append(", ")
			.append(param);
		}
		sb.append(") {\n\t\tthis.ordinal = ordinal;\n");
		for (TypeScriptField field : generatedClass.getFields()) {
			sb.append("\t\tthis.")
			.append(field.getFieldName())
			.append(" = ")
			.append(field.getFieldName())
			.append(";\n");
		}
		sb.append("\t}\n")
		
		// Values
		.append("\n\tstatic readonly values:")
		.append(generatedClass.getClazz().getSimpleName())
		.append("[] = {\n");
		int i = 0;
		for (TypeScriptEnumConstant constant : generatedClass.getConstants()) {
			sb.append(i++ == 0 ? "\t\t" : ",\n\t\t")
			.append(generatedClass.getClazz().getSimpleName())
			.append(".")
			.append(constant.getName());
		}
		sb.append("\n\t}");
		
		// Closing bracket
		sb.append("\n\n}");

		return sb.toString();
	}

	/**
	 * Checks whether the base classes defined in <code>TypeScriptGenConfig</code> contains the given class.
	 * @param clazz
	 * @return
	 */
	public static boolean baseClassesContains(Class<?> clazz) {
		for (Class<?> clz : TypeScriptGenConfig.BASE_CLASSES) {
			if (clz == clazz) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Checks whether a <code>Method</code> array contains the getter for a given <code>Field</code>.
	 * A method is considered to be a getter if it has no parameters, its return type matches the field type, 
	 * and its name starts with 'get' (or 'is' for booleans) and contains the field name.
	 */
	public static boolean containsGetter(Field field, Method[] methods) {
		return findGetter(field, methods) != null;
	}

	/**
	 * Finds one method from a <code>Method</code> array that could be a getter for a given <code>Field</code>.
	 * A method is considered to be a getter if it has no parameters, its return type matches the field type, 
	 * and its name starts with 'get' (or 'is' for booleans) and contains the field name.
	 * @param field The field to find a getter for.
	 * @param methods The <code>Method</code> array to search in.
	 * @return A getter method for the given field, or <code>null</code> of no suitable method was found.
	 */
	public static Method findGetter(Field field, Method[] methods) {
		for (Method method : methods) {
			if (method.getParameterCount() > 0) {
				continue;
			}
			if (method.getReturnType() != field.getType()) {
				continue;
			}
			if (!(getType(field.getType()) == Type.BOOLEAN && method.getName().startsWith("is")) && !method.getName().startsWith("get")) {
				continue;
			}
			if (!method.getName().endsWith(field.getName().substring(1))) {
				continue;
			}
			return method;
		}
		return null;
	}

	/**
	 * Checks whether a <code>Method</code> array contains the setter for a given <code>Field</code>.
	 * A method is considered to be a setter if it has exactly one parameter of the same type of as the field, 
	 * and its name starts with 'set' and contains the field name.
	 */
	public static boolean containsSetter(Field field, Method[] methods) {
		return findSetter(field, methods) != null;
	}

	/**
	 * Finds one method from a <code>Method</code> array that could be a setter for a given <code>Field</code>.
	 * A method is considered to be a setter if it has exactly one parameter of the same type of as the field, 
	 * and its name starts with 'set' and contains the field name.
	 * @param field The field to find a setter for.
	 * @param methods The <code>Method</code> array to search in.
	 * @return A setter method for the given field, or <code>null</code> of no suitable method was found.
	 */
	public static Method findSetter(Field field, Method[] methods) {
		for (Method method : methods) {
			if (method.getParameterCount() != 1) {
				continue;
			}
			if (method.getParameterTypes()[0] != field.getType()) {
				continue;
			}
			if (!method.getName().startsWith("set")) {
				continue;
			}
			if (!method.getName().endsWith(field.getName().substring(1))) {
				continue;
			}
			return method;
		}
		return null;
	}
	
	public static String getRelativePath(Class<?> from, Class<?> to) {
		StringBuilder sb = new StringBuilder();
		String[] fromPath = from.getPackage().getName().replaceFirst(TypeScriptGenConfig.BASE_PACKAGE + ".", "").split("\\.");
		String[] toPath = to.getPackage().getName().replaceFirst(TypeScriptGenConfig.BASE_PACKAGE + ".", "").split("\\.");
		int i = 0;
		while (i < Math.min(fromPath.length, toPath.length)) {
			if (!fromPath[i].equals(toPath[i])) {
				break;
			}
			i++;
		}
		if (i == fromPath.length) {
			sb.append("./");
		}
		else {
			for (int j = 0; j < fromPath.length - i; j++) {
				sb.append("../");
			}
		}
		if (i < toPath.length) {
			for (int j = i; j < toPath.length; j++) {
				sb.append(toPath[j] + "/");
			}
		}
		return sb.toString();
	}
	
	/**
	 * Generates a TypeScript filename based on the class name of the generated class type.
	 * Does not include the '.ts' extension at the end.
	 */
	public static String getFilename(TypeScriptClass type) {
		String result = StringUtils.camelToHyphenDelimited(type.getClazz().getSimpleName(), Capitalization.ALL_LOWER);
		if (type instanceof TypeScriptClass) {
			return result + ".class";
		}
		else if (type instanceof TypeScriptEnum) {
			return result + ".enum";
		}
		return result;
	}
	
	/**
	 * Generates a TypeScript filename based on the class name of the generated class type, including the '.ts' file extension.
	 */
	public static String getFilenameWithExtension(TypeScriptClass type) {
		return getFilename(type) + ".ts";
	}
	
}