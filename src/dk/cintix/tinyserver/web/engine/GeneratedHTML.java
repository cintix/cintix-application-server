package dk.cintix.tinyserver.web.engine;

/**
 *
 * @author migo
 */
public class GeneratedHTML {

        private Document document;
        private String html;

        public GeneratedHTML() {
        }

        public GeneratedHTML(Document document, String html) {
            this.document = document;
            this.html = html;
        }

        public Document getDocument() {
            return document;
        }

        public void setDocument(Document document) {
            this.document = document;
        }

        public String getHtml() {
            return html;
        }

        public void setHtml(String html) {
            this.html = html;
        }

        @Override
        public String toString() {
            return "GeneratedHTML{" + "document=" + document + ", html=" + html + '}';
        }

}
