package info.hubbitus;

import com.sun.codemodel.JAnnotatable;
import com.sun.codemodel.JAnnotationUse;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.util.JavadocEscapeWriter;
import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.Plugin;
import com.sun.tools.xjc.model.CClassInfo;
import com.sun.tools.xjc.model.CPropertyInfo;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.tools.xjc.outline.Outline;
import com.sun.tools.xjc.reader.xmlschema.bindinfo.BindInfo;
import com.sun.xml.xsom.XSAnnotation;
import com.sun.xml.xsom.XSComponent;
import com.sun.xml.xsom.impl.AttributeUseImpl;
import com.sun.xml.xsom.impl.ParticleImpl;
import com.sun.xml.xsom.impl.util.SchemaWriter;
import info.hubbitus.annotation.XsdInfo;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.Map;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * XJC plugin to place XSD documentation annotations ({@code <xs:annotation><xs:documentation>}) for
 * runtime usage on classes and fields.
 *
 * F.e. for XSD declaration:
 *
 * <pre>{@code
 * 	<xs:complexType name="Customer">
 * 		<xs:annotation>
 * 			<xs:documentation>Пользователь</xs:documentation>
 * 		</xs:annotation>
 * 		<xs:sequence>
 * 			<xs:element name="name" type="xs:string">
 * 				<xs:annotation>
 * 					<xs:documentation>Фамилия и имя</xs:documentation>
 * 				</xs:annotation>
 * 			</xs:element>
 * 			<xs:element name="age" type="xs:positiveInteger">
 * 				<xs:annotation>
 * 					<xs:documentation>Возраст</xs:documentation>
 * 				</xs:annotation>
 * 			</xs:element>
 * 		</xs:sequence>
 * 	</xs:complexType>
 * }</pre> Will be generated (stripped, base annotations and methods omitted):
 *
 *
 * @see <a href="https://blog.jooq.org/tag/xjc-plugin/">How to Implement Your Own XJC Plugin to
 * Generate toString(), equals(), and hashCode() Methods</a>
 * @see <a href="http://www.archive.ricston.com/blog/xjc-plugin/">Creating an XJC plugin</a>
 * @see
 * <a href="https://stackoverflow.com/questions/43233629/xjc-java-classes-generation-where-fields-have-the-same-name-as-xmlelement/43381317#43381317">SOq
 * xjc java classes generation, where fields have the same name as @XmlElement</a>
 * @see
 * <a href="https://stackoverflow.com/questions/21606248/jaxb-convert-non-ascii-characters-to-ascii-characters/21780020#21780020">JAXB
 * convert non-ASCII characters to ASCII characters</a>
 *
 * @see <a href="https://www.javacodegeeks.com/2011/12/reusing-generated-jaxb-classes.html">Reusing
 * generated jaxb classes</a>
 *
 * @author Pavel Alexeev.
 * @since 2019-01-17 03:34.
 */
public class XJCPluginDescriptionAnnotation extends Plugin {

    public static final String NAME = "XPluginDescriptionAnnotation";

    @Override
    public String getOptionName() {
        return NAME;
    }

    @Override
    public int parseArgument(Options opt, String[] args, int i) {
        return 0;
    }

    @Override
    public String getUsage() {
        return "  -XPluginDescriptionAnnotation    :" +
            "  xjc plugin for bring XSD descriptions as annotations";
    }

    @Override
    public boolean run(Outline model, Options opt, ErrorHandler errorHandler) throws SAXException {
        try {
            model.getClasses().forEach((ClassOutline c) -> {
                CClassInfo classInfo = c.target;
                final String description = classInfoGetDescriptionAnnotation(classInfo);
                
                if (description != null) {
                    annotateUnescaped(c.implClass, XsdInfo.class,
                        Map.of("name", description)
                    );
                }

                c.implClass.fields().forEach((String name, JFieldVar jField) -> {
                    CPropertyInfo property = classInfo.getProperties().stream()
                        .filter(it -> it.getName(false).equals(jField.name()))
                        .findAny()
                        .orElseThrow(() -> new IllegalStateException("Can't find property [" +
                            jField.name() + "] in class [" + classInfo.getTypeName() + "]"));

                    final String annotation = fieldGetDescriptionAnnotation(property);
                    if (annotation != null) {
                        annotateUnescaped(jField, XsdInfo.class,
                            Map.of("name", annotation)
                        );
                    }
                });
            }
            );
        } catch (Exception e) {
            e.printStackTrace();
            errorHandler.error(new SAXParseException(e.getMessage(), null, e));
            return false;
        }
        return true;
    }

    /**
     * Workaround method! By default annotation russian values escaped like
     * '\u0417\u0430\u0433\u043e\u043b\u043e\u0432\u043e\u043a
     * \u0437\u0430\u044f\u0432\u043b\u0435\u043d\u0438\u044f' instead of "Заголовок заявления". It
     * happened in: {
     *
     * @see com.sun.codemodel.JExpr#quotify(char, java.lang.String)} (call from {
     * @see com.sun.codemodel.JStringLiteralUnescaped#generate(com.sun.codemodel.JFormatter)}). So
     * it is hardcoded in XJC. We search graceful way to override it. We want it be unescaped. See
     * <a href="https://github.com/javaee/jaxb-codemodel/issues/30">upstream bug</a>
     *
     * So, instead of just do:      <code>
     * jField.annotate(XsdInfo.class).param("name", "Русское описание");
     * </code> You may do instead:      <code>
     * annotate(jField, XsdInfo.class, Map.Of("name", "Русское описание"))
     * </code>
     */
    @SuppressWarnings("unchecked")
    private static void annotateUnescaped(
        JAnnotatable object,
        Class<? extends Annotation> annotationClass,
        Map<String, String> parameters) {

        assert parameters.size() > 0;

        JAnnotationUse annotation = object.annotate(annotationClass);
        final Map<String, JExpression> map; // Lambda requires final variable

        // {@see com.sun.codemodel.JAnnotationUse.memberValues}
        Map<String, JExpression> memberMap =
            (Map<String, JExpression>) getPrivateField(annotation, "memberValues");

        if (null == memberMap) {
            // Just init memberValues private map, such key will be replaced
            annotation.param(parameters.keySet().iterator().next(), "");
            map = (Map<String, JExpression>) getPrivateField(annotation, "memberValues");
        } else {
            map = memberMap;
        }
        assert map.size() > 0;
        parameters.forEach((key, val) -> {
            map.put(key, new JStringLiteralUnescaped(val));
        });
    }

    private static Object getPrivateField(Object obj, String fieldName) {
        try {
            Field f = obj.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            return f.get(obj);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException("Can't get field [" + fieldName + "] from object [" +
                obj + "]!", e);
        }
    }

    static private String classInfoGetDescriptionAnnotation(CClassInfo classInfo) {
        String description = "";
        if (null != (classInfo.getSchemaComponent()).getAnnotation()) {
            description = ((BindInfo) (classInfo.getSchemaComponent()).getAnnotation().
                getAnnotation()).getDocumentation();
        }
        return description == null ? null : description.trim();
    }

    static private String fieldGetDescriptionAnnotation(CPropertyInfo propertyInfo) {
        String description = "";

        XSComponent schemaComponent = propertyInfo.getSchemaComponent();
        XSAnnotation annotation = null;

        //<xs:complexType name="TDocumentRefer">
        //		<xs:attribute name="documentID" use="required">
        //			<xs:annotation>
        //				<xs:documentation>Идентификатор документа</xs:documentation>
        if (schemaComponent instanceof AttributeUseImpl) {
            final AttributeUseImpl attribute = (AttributeUseImpl) propertyInfo.getSchemaComponent();
            annotation = attribute.getDecl().getAnnotation();
        } // <xs:complexType name="TBasicInterdepStatement">
        //		<xs:element name="header" type="stCom:TInterdepStatementHeader" minOccurs="0">
        //				<xs:annotation>
        //					<xs:documentation>Заголовок заявления</xs:documentation>
        else if (schemaComponent instanceof ParticleImpl) {
            annotation = ((ParticleImpl) schemaComponent).getTerm().getAnnotation();

        }

        if (annotation != null && annotation.getAnnotation() instanceof BindInfo) {
            final BindInfo bindInfo = (BindInfo) annotation.getAnnotation();
            description = bindInfo.getDocumentation();
            return description == null ? null : description.trim();
        }

        return null;
    }

//	@Override
//	public void postProcessModel(Model model, ErrorHandler errorHandler) {
//		super.postProcessModel(model, errorHandler);
//	}
    /**
     * See implementation in {
     *
     * @see ClassSelector#addSchemaFragmentJavadoc(CClassInfo, XSComponent)}
     */
    static private String getXsdXmlDeclaration(XSComponent sc) {
        StringWriter out = new StringWriter();
        SchemaWriter sw = new SchemaWriter(new JavadocEscapeWriter(out) {
            @Override
            public void write(int ch) throws IOException {
                out.write(ch);
            }
        });
        sc.visit(sw);
        return out.toString().trim();
    }

}
