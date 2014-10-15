package pocketknife.internal.codegen;

import android.os.Build;
import com.squareup.javawriter.JavaWriter;
import pocketknife.InjectArgument;
import pocketknife.NotRequired;
import pocketknife.SaveState;
import pocketknife.internal.GeneratedAdapters;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static javax.lang.model.element.ElementKind.CLASS;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.STATIC;
import static javax.tools.Diagnostic.Kind.ERROR;
import static pocketknife.internal.GeneratedAdapters.ANDROID_PREFIX;
import static pocketknife.internal.GeneratedAdapters.JAVA_PREFIX;

public class PocketKnifeProcessor extends AbstractProcessor {

    private static final CharSequence SERIALIZABLE =  "java.io.Serializable";
    private static final CharSequence PARCELABLE = "android.os.Parcelable";
//    private static final CharSequence BINDER = "android.os.IBinder"; // Api 18+
    private static final CharSequence BUNDLE = "android.os.Bundle";
    private static final CharSequence STRING = "java.lang.String";
    private static final CharSequence CHAR_SEQUENCE = "java.lang.CharSequence";
    private static final CharSequence INTEGER = "java.lang.Integer";
    private static final CharSequence ARRAY_LIST = "java.util.ArrayList";
    private static final CharSequence SPARSE_ARRAY = "android.util.SparseArray";

    private TypeMirror serializableType;
    private TypeMirror parcelableType;
//    private TypeMirror binderType; // API 18+
    private TypeMirror bundleType;
    private TypeMirror stringType;
    private TypeMirror charSequenceType;
    private TypeMirror integerType;
    private TypeMirror arrayListType;
    private TypeMirror sparseArrayType;

    private Messager messager;
    private Elements elements;
    private Types types;
    private Filer filer;

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        messager = processingEnv.getMessager();
        elements = processingEnv.getElementUtils();
        types = processingEnv.getTypeUtils();
        filer = processingEnv.getFiler();
        setupComparableTypes();
    }

    private void setupComparableTypes() {
        if (serializableType == null) {
            Element element = elements.getTypeElement(SERIALIZABLE);
            if (element == null) {
                throw new IllegalStateException("Unable to find Serializable type");
            }
            serializableType = element.asType();
        }
        if (parcelableType == null) {
            Element element = elements.getTypeElement(PARCELABLE);
            if (element == null) {
                throw new IllegalStateException("Unable to find Parcelable type");
            }
            parcelableType = element.asType();
        }
//        if (binderType == null) { // API 18+
//            Element element = elements.getTypeElement(BINDER);
//            if (element == null) {
//                throw new IllegalStateException("Unable to find Binder type");
//            }
//            binderType = element.asType();
//        }
        if (bundleType == null) {
            Element element = elements.getTypeElement(BUNDLE);
            if (element == null) {
                throw new IllegalStateException("Unable to find Bundle type");
            }
            bundleType = element.asType();
        }
        if (stringType == null) {
            Element element = elements.getTypeElement(STRING);
            if (element == null) {
                throw new IllegalStateException("Unable to find String type");
            }
            stringType = element.asType();
        }
        if (charSequenceType == null) {
            Element element = elements.getTypeElement(CHAR_SEQUENCE);
            if (element == null) {
                throw new IllegalStateException("Unable to find CharSequence type");
            }
            charSequenceType = element.asType();
        }
        if (integerType == null) {
            Element element = elements.getTypeElement(INTEGER);
            if (element == null) {
                throw new IllegalStateException("Unable to find Integer type");
            }
            integerType = element.asType();
        }
        if (arrayListType == null) {
            Element element = elements.getTypeElement(ARRAY_LIST);
            if (element == null) {
                throw new IllegalStateException("Unable to find ArrayList type");
            }
            arrayListType = element.asType();
        }
        if (sparseArrayType == null) {
            Element element = elements.getTypeElement(SPARSE_ARRAY);
            if (element == null) {
                throw new IllegalStateException("Unable to find SparseArray type");
            }
            sparseArrayType = element.asType();
        }
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return new HashSet<String>(Arrays.asList(SaveState.class.getCanonicalName(), InjectArgument.class.getCanonicalName()));
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Map<TypeElement, BundleAdapterGenerator> targetClassMap = findAndParseTargets(roundEnv);

        for (Map.Entry<TypeElement, BundleAdapterGenerator> entry : targetClassMap.entrySet()) {
            TypeElement typeElement = entry.getKey();
            BundleAdapterGenerator bundleAdapterGenerator = entry.getValue();
            JavaWriter javaWriter = null;
            try {
                JavaFileObject jfo = filer.createSourceFile(bundleAdapterGenerator.getFqcn(), typeElement);
                javaWriter = new JavaWriter(jfo.openWriter());
                bundleAdapterGenerator.generate(javaWriter);
            } catch (Exception e) {
                error(typeElement, "Unable to write injector for type %s: %s", typeElement, e.getMessage());
            } finally {
                if (javaWriter != null) {
                    try {
                        javaWriter.close();
                    } catch (IOException e) {
                        error(null, e.getMessage());
                    }
                }
            }
        }


        return false;
    }

    private Map<TypeElement, BundleAdapterGenerator> findAndParseTargets(RoundEnvironment env) {
        Map<TypeElement, BundleAdapterGenerator> targetClassMap = new LinkedHashMap<TypeElement, BundleAdapterGenerator>();
        Set<String> erasedTargetNames = new LinkedHashSet<String>();

        // Process each @SaveState
        for (Element element : env.getElementsAnnotatedWith(SaveState.class)) {
            try {
                parseSaveState(element, targetClassMap, erasedTargetNames);
            } catch (Exception e) {
                StringWriter stackTrace = new StringWriter();
                e.printStackTrace(new PrintWriter(stackTrace));

                error(element, "Unable to generate bundle adapter for @SaveState.\n\n%s", stackTrace);
            }
        }

        // Process each @InjectAnnotation
        for (Element element : env.getElementsAnnotatedWith(InjectArgument.class)) {
            try {
                parseInjectAnnotation(element, targetClassMap, erasedTargetNames);
            } catch (Exception e) {
                StringWriter stackTrace = new StringWriter();
                e.printStackTrace(new PrintWriter(stackTrace));

                error(element, "Unable to generate bundle adapter for @InjectAnnotation.\n\n%s", stackTrace);
            }
        }

        return targetClassMap;
    }

    private void parseSaveState(Element element, Map<TypeElement, BundleAdapterGenerator> targetClassMap, Set<String> erasedTargetNames)
            throws ClassNotFoundException {
        TypeElement enclosingElement = (TypeElement) element.getEnclosingElement();

        // Verify that the target has all the appropriate information for type
        TypeMirror elementType = element.asType();
        if (elementType instanceof TypeVariable) {
            TypeVariable typeVariable = (TypeVariable) elementType;
            elementType = typeVariable.getUpperBound();
        }

        String bundleType = getBundleType(element, elementType);
        Boolean needsToBeCast = needsToBeCast(element, elementType);

        boolean hasError = !areSaveStateArgumentsValid(element);
        hasError |= bundleType == null;
        hasError |= needsToBeCast == null;
        hasError |= !isValidForGeneratedCode(SaveState.class, "fields", element);
        hasError |= isBindingInWrongPackage(SaveState.class, element);

        if (hasError) {
            return;
        }

        // Assemble information on the injection point.
        String name = element.getSimpleName().toString();
        NotRequired notRequired = element.getAnnotation(NotRequired.class);
        boolean required = notRequired == null;
        int minSdk = Build.VERSION_CODES.FROYO;
        if (!required) {
            minSdk = notRequired.value();
        }
        boolean canHaveDefault = !required && canHaveDefault(elementType, minSdk);

        BundleAdapterGenerator bundleAdapterGenerator = getOrCreateTargetClass(targetClassMap, enclosingElement);
        BundleFieldBinding binding = new BundleFieldBinding(name, elementType.toString(), bundleType, needsToBeCast, canHaveDefault, required);
        bundleAdapterGenerator.addField(binding);

        // Add the type-erased version to the valid injection targets set.
        erasedTargetNames.add(enclosingElement.toString());
    }

    private void parseInjectAnnotation(Element element, Map<TypeElement, BundleAdapterGenerator> targetClassMap, Set<String> erasedTargetNames) {
        TypeElement enclosingElement = (TypeElement) element.getEnclosingElement();

        // Verify that the target has all the appropriate information for type
        TypeMirror elementType = element.asType();
        if (element instanceof TypeVariable) {
            TypeVariable typeVariable = (TypeVariable) elementType;
            elementType = typeVariable.getUpperBound();
        }

        String bundleType = getBundleType(element, elementType);

        boolean hasError = !areInjectArgumentArgumentsValid(element);
        hasError |= bundleType == null;
        hasError |= !isValidForGeneratedCode(InjectArgument.class, "fields", element);
        hasError |= isBindingInWrongPackage(InjectArgument.class, element);

        if (hasError) {
            return;
        }

        // Assemble information on the injection point
        String name = element.getSimpleName().toString();
        InjectArgument annotation = element.getAnnotation(InjectArgument.class);
        String key = annotation.value();
        NotRequired notRequired = element.getAnnotation(NotRequired.class);
        boolean required = notRequired == null;
        int minSdk = Build.VERSION_CODES.FROYO;
        if (!required) {
            minSdk = notRequired.value();
        }
        boolean canHaveDefault = !required && canHaveDefault(elementType, minSdk);

        BundleAdapterGenerator bundleAdapterGenerator = getOrCreateTargetClass(targetClassMap, enclosingElement);
        BundleFieldBinding binding = new BundleFieldBinding(name, elementType.toString(), bundleType, key, isPrimitive(elementType), canHaveDefault, required);
        bundleAdapterGenerator.addField(binding);

        // Add the type-erased version to the valid injection targets set.
        erasedTargetNames.add(enclosingElement.toString());
    }

    private boolean areSaveStateArgumentsValid(Element element) {
        NotRequired notRequired = element.getAnnotation(NotRequired.class);
        if (notRequired != null && notRequired.value() < Build.VERSION_CODES.FROYO) {
            error(element, "NotRequired value must be FROYO(8)+");
            return false;
        }
        return true;
    }

    private boolean areInjectArgumentArgumentsValid(Element element) {
        NotRequired notRequired = element.getAnnotation(NotRequired.class);
        if (notRequired != null && notRequired.value() < Build.VERSION_CODES.FROYO) {
            error(element, "NotRequired value must be FROYO(8)+");
            return false;
        }
        InjectArgument injectArgument = element.getAnnotation(InjectArgument.class);
        if (injectArgument.value() == null || injectArgument.value().trim().isEmpty()) {
            error(element, "InjectAnnotation value must not be empty");
            return false;
        }
        return true;
    }

    private boolean isValidForGeneratedCode(Class<? extends Annotation> annotationClass, String targetThing, Element element) {
        boolean isValid = true;
        TypeElement enclosingElement = (TypeElement) element.getEnclosingElement();

        // Verify method modifiers
        Set<Modifier> modifiers = element.getModifiers();
        if (modifiers.contains(PRIVATE) || modifiers.contains(STATIC)) {
            error(element, "@%s %s must not be private, protected, or static. (%s.%s)",
                    annotationClass.getSimpleName(), targetThing, enclosingElement.getQualifiedName(),
                    element.getSimpleName());
            isValid = false;
        }

        // Verify Containing type.
        if (enclosingElement.getKind() != CLASS) {
            error(enclosingElement, "@%s %s may only be contained in classes. (%s.%s)",
                    annotationClass.getSimpleName(), targetThing, enclosingElement.getQualifiedName(),
                    element.getSimpleName());
            isValid = false;
        }

        // Verify containing class visibility is not private
        if (enclosingElement.getModifiers().contains(PRIVATE)) {
            error(enclosingElement, "@%s %s may not be contained in private classes (%s.%s)", annotationClass.getSimpleName(), targetThing,
                    enclosingElement.getQualifiedName(), element.getSimpleName());
            isValid = false;
        }

        return isValid;
    }

    private boolean isBindingInWrongPackage(Class<? extends Annotation> annotationClass, Element element) {
        TypeElement enclosingElement = (TypeElement) element.getEnclosingElement();
        String qualifiedName = enclosingElement.getQualifiedName().toString();

        if (qualifiedName.startsWith(ANDROID_PREFIX)) {
            error(element, "@%s-annotated class incorrectly in Android framework package. (%s)",
                    annotationClass.getSimpleName(), qualifiedName);
            return true;
        }
        if (qualifiedName.startsWith(JAVA_PREFIX)) {
            error(element, "@%s-annotated class incorrectly in Java framework package. (%s)",
                    annotationClass.getSimpleName(), qualifiedName);
            return true;
        }

        return false;
    }

    private BundleAdapterGenerator getOrCreateTargetClass(Map<TypeElement, BundleAdapterGenerator> targetClassMap, TypeElement enclosingElement) {
        BundleAdapterGenerator bundleAdapterGenerator = targetClassMap.get(enclosingElement);
        if (bundleAdapterGenerator == null) {
            String targetType = enclosingElement.getQualifiedName().toString();
            String classPackage = getPackageName(enclosingElement);
            String className = getClassName(enclosingElement, classPackage) + GeneratedAdapters.BUNDLE_ADAPTER_SUFFIX;

            bundleAdapterGenerator = new BundleAdapterGenerator(classPackage, className, targetType);
            targetClassMap.put(enclosingElement, bundleAdapterGenerator);
        }
        return bundleAdapterGenerator;
    }

    private void error(Element element, String message, Object... args) {
        if (args.length > 0) {
            message = String.format(message, args);
        }
        messager.printMessage(ERROR, message, element);
    }

    private String getPackageName(TypeElement type) {
        return elements.getPackageOf(type).getQualifiedName().toString();
    }

    private String getClassName(TypeElement typeElement, String packageName) {
        int packageLen = packageName.length() + 1;
        return typeElement.getQualifiedName().toString().substring(packageLen).replace('.', '$');
    }

    private boolean canHaveDefault(TypeMirror type, int minSdk) {
        return isPrimitive(type) || minSdk >= Build.VERSION_CODES.HONEYCOMB_MR1 && types.isAssignable(type, charSequenceType);
    }

    private String getBundleType(Element element, TypeMirror type) {
        try {
            // Check Primitive
            if (isPrimitive(type)) {
                return getPrimitiveBundleType(type);
            }

            // Check Array
            if (TypeKind.ARRAY == type.getKind() && type instanceof ArrayType) {
                String componentType = getArrayComponentBundleType(((ArrayType) type).getComponentType());
                if (componentType != null && !componentType.isEmpty()) {
                    return componentType.concat("Array");
                }
            }

            // Check ArrayList
            if (types.isAssignable(types.erasure(type), arrayListType)) {
                String arrayListType = getArrayListBundleType(type);
                if (arrayListType != null && !arrayListType.isEmpty()) {
                    return arrayListType;
                }
            }

            // Check Sparse Parcelable Array
            if (isSparesParcelableArray(type)) {
                return "SparseParcelableArray";
            }

            // Other Types
            if (types.isAssignable(type, bundleType)) {
                return "Bundle";
            }

            String aggregateBundleType = getAggregateBundleType(type);
            if (aggregateBundleType != null && !aggregateBundleType.isEmpty()) {
                return aggregateBundleType;
            }

            if (types.isAssignable(type, serializableType)) {
                return "Serializable";
            }

        } catch (InvalidTypeException e) {
            error(element, "%s", e.getMessage());
            return null;
        }

        error(element, "Invalid bundle type '%s'", type.toString());
        return null;
    }

    private boolean isPrimitive(TypeMirror type) {
        return type.getKind().isPrimitive();
    }

    private String getPrimitiveBundleType(TypeMirror type) throws InvalidTypeException {
        // No unboxing due to the nullable nature of Boxed primitives.
        switch (type.getKind()) {
            case BOOLEAN:
                return "Boolean";
            case BYTE:
                return "Byte";
            case SHORT:
                return "Short";
            case INT:
                return "Int";
            case LONG:
                return "Long";
            case CHAR:
                return "Char";
            case FLOAT:
                return "Float";
            case DOUBLE:
                return "Double";
            default:
                throw new InvalidTypeException("Primitive", type);
        }
    }

    private String getArrayComponentBundleType(TypeMirror type) throws InvalidTypeException {
        if (isPrimitive(type)) {
            try {
                return getPrimitiveBundleType(type);
            } catch (InvalidTypeException e) {
                throw new InvalidTypeException("Array", type);
            }
        }
        return getAggregateBundleType(type);
    }

    private String getArrayListBundleType(TypeMirror type) {
        if (type instanceof DeclaredType) {
            List<? extends TypeMirror> typeArguments = ((DeclaredType) type).getTypeArguments();
            if (typeArguments.size() == 1) {
                String arrayListComponentType = getArrayListComponentBundleType(typeArguments.get(0));
                if (arrayListComponentType != null && !arrayListComponentType.isEmpty()) {
                    return arrayListComponentType.concat("ArrayList");
                }
            }
        }
        return null;
    }

    private String getArrayListComponentBundleType(TypeMirror type) {
        if (types.isAssignable(type, integerType)) {
            return "Integer";
        }
        return getAggregateBundleType(type);
    }

    private String getAggregateBundleType(TypeMirror type) {
        if (types.isAssignable(type, stringType)) { // String is subtype of charsequence should go first.
            return "String";
        }
        if (types.isAssignable(type, charSequenceType)) {
            return "CharSequence";
        }
        if (types.isAssignable(type, parcelableType)) {
            return "Parcelable";
        }
        return null;
    }

    private boolean isSparesParcelableArray(TypeMirror type) {
        if (types.isAssignable(types.erasure(type), sparseArrayType) && type instanceof DeclaredType) {
            List<? extends TypeMirror> typeArguments = ((DeclaredType) type).getTypeArguments();
            if (typeArguments.size() == 1) {
                return types.isAssignable(typeArguments.get(0), parcelableType);
            }
        }
        return false;
    }

    private Boolean needsToBeCast(Element element, TypeMirror type) {
        if (isPrimitive(type)) {
            return false;
        }

        // Check Array
        if (TypeKind.ARRAY == type.getKind() && type instanceof ArrayType) {
            Boolean result = needToCastArrayComponentType(((ArrayType) type).getComponentType());
            if (result != null) {
                return result;
            }
        }

        // ArrayList
        if (types.isAssignable(types.erasure(type), arrayListType)) {
            Boolean result = needToCastArrayList(type);
            if (result != null) {
                return result;
            }
        }

        // Sparse Parcelable Array
        if (types.isAssignable(types.erasure(type), sparseArrayType)) {
            Boolean result = needToCastSparseParcelableArray(type);
            if (result != null) {
                return result;
            }
        }

        // Other types
        Boolean result = needToCastAggregateBundleType(type);
        if (result != null) {
            return result;
        }

        if (types.isAssignable(type, bundleType)) {
            return !types.isSameType(type, bundleType);
        }

        if (types.isAssignable(type, serializableType)) {
            return !types.isSameType(type, serializableType);
        }

        error(element, "Error invalid bundle type '%s'", type);
        return null;
    }

    private Boolean needToCastArrayComponentType(TypeMirror type) {
        if (isPrimitive(type)) {
            return false;
        }
        return needToCastAggregateBundleType(type);
    }

    private Boolean needToCastArrayList(TypeMirror type) {
        if (type instanceof DeclaredType) {
            List<? extends TypeMirror> typeArguments = ((DeclaredType) type).getTypeArguments();
            if (typeArguments.size() == 1) {
                if (types.isAssignable(type, integerType)) {
                    return !types.isSameType(type, integerType);
                }
                Boolean result = needToCastAggregateBundleType(type);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    private Boolean needToCastAggregateBundleType(TypeMirror type) {
        if (types.isAssignable(type, charSequenceType)) {
            return !types.isSameType(type, charSequenceType);
        }
        if (types.isAssignable(type, stringType)) {
            return !types.isSameType(type, stringType);
        }
        if (types.isAssignable(type, parcelableType)) {
            return !types.isSameType(type, parcelableType);
        }
        return null;
    }

    private Boolean needToCastSparseParcelableArray(TypeMirror type) {
        if (type instanceof DeclaredType) {
            List<? extends TypeMirror> typeArguments = ((DeclaredType) type).getTypeArguments();
            if (typeArguments.size() == 1 && types.isAssignable(typeArguments.get(0), parcelableType)) {
                return false;
            }
        }
        return null;
    }
}
