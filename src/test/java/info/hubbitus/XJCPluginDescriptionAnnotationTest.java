package info.hubbitus;

/**
 *
 * @author Francesco Illuminati <fillumina@gmail.com>
 */
public class XJCPluginDescriptionAnnotationTest extends RunXJC2MojoTestHelper {

    @Override
    public String getFolderName() {
        return "example";
    }

    public void test() {
        element("Customer")
            .classAnnotations()
                .annotation("XsdInfo")
                    .assertParam("name", "this is customer annotation").end()
                    .end()
            .attribute("name")
                .annotation("XsdInfo")
                    .assertParam("name", "this is name annotation").end()
                    .end()
            .attribute("age")
                .annotation("XsdInfo")
                    .assertParam("name", "this is age annotation").end()
                    .end()
            .attribute("address").assertAnnotationNotPresent("XsdInfo").end()
            .attribute("telephone").assertAnnotationNotPresent("XsdInfo").end()
            .attribute("title").assertAnnotationNotPresent("XsdInfo");
            
    }

}
