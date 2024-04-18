
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CrptApi {
    public static void main(String[] args) throws InterruptedException, JsonProcessingException {
        Description description = new Description("123");
        List<Product> products = new ArrayList<>();
        products.add(new Product("cert", "2024-01-01", "123", "owner_inn", "producer_inn",
                "2024-01-01", "tnved_code", "uit_code", "uitu_code"));
        CrptApi crptApi = CrptApiBuilder.build(TimeUnit.SECONDS, 10);
        for (int i = 0; i < 10; i++) {
            Document document = new Document(description, "doc_id", "status", "type",
                    true, "owner_inn", "participant_inn", "producer_inn",
                    "2024-01-01", "production_type", products, "2024-01-01", "reg_number");
            document.docId = String.valueOf(i);
            crptApi.create(document);
            ObjectMapper objectMapper1= new ObjectMapper();
            String json = objectMapper1.writeValueAsString(document);
            System.out.println(json);
        }

    }

    private final TimeUnit timeUnit;
    private final int requestLimit;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Queue<Document> requestQueue;
    private ScheduledExecutorService scheduler;

    private final String url = "https://ismp.crpt.ru/api/v3/lk/documents/create";


    /**
     * Конструктор CrptApi.
     *
     * @param timeUnit     Единица измерения времени для периода выполнения задач в планировщике.
     * @param requestLimit Максимальное количество запросов, которое может быть обработано в единицу времени.
     */
    private CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.timeUnit = timeUnit;
        this.requestLimit = requestLimit;
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        this.requestQueue = new LinkedList<>();
    }

    /**
     * Добавляет документ в очередь запросов.
     * Если планировщик равен null, он инициализируется и запускается.
     *
     * @param doc Документ для добавления в очередь запросов.
     */
    public synchronized void create(Document doc) {
        if (scheduler == null) {
            scheduler = Executors.newScheduledThreadPool(1);
            start();
        }
        requestQueue.offer(doc);
        System.out.println("Request added to queue: " + doc);
    }

    /**
     * Запускает выполнение задачи по обработке очереди запросов с заданным интервалом времени.
     */
    private void start() {
        scheduler.scheduleAtFixedRate(this::processQueue, 0, 1, timeUnit);
    }

    /**
     * Обрабатывает элементы очереди запросов.
     * В случае если очередь пустая, планироващик останавливается и объект присваивается null.
     */
    private void processQueue() {
        int processedRequests = 0;
        while (!requestQueue.isEmpty() && processedRequests < requestLimit) {
            processRequest(requestQueue.poll());
            processedRequests++;
        }
        if (requestQueue.isEmpty()) {
            scheduler.shutdown();
            scheduler = null;
        }
    }

    /**
     * Обрабатывает запрос.
     *
     * @param doc Документ передаваемый в запрос.
     * @exception IOException в случае выбрасывания исключения в методе send() ли writeValueAsString()
     * @exception InterruptedException в случае прерывания работы метода send()
     */
    private void processRequest(Document doc) {
        try {
            System.out.println("Processing request: " + doc);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(doc)))
                    .build();
            httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Builder предоставляющий экземпляр класса CrptApi.
     */
    public static final class CrptApiBuilder {
        /**
         * Создает новый экземпляр CrptApiBuilder.
         */
        public CrptApiBuilder() {
        }

        /**
         * Статический метод для создания экземпляра CrptApi с заданными параметрами.
         *
         * @param timeUnit     Единица измерения времени для периода выполнения задач в планировщике.
         * @param requestLimit Максимальное количество запросов, которое может быть обработано в единицу времени.
         * @return Новый экземпляр CrptApi.
         * @throws IllegalArgumentException Если значение количества запросов меньше 1.
         */
        public static CrptApi build(TimeUnit timeUnit, int requestLimit) {
            if (requestLimit < 1) {
                throw new IllegalArgumentException("Request limit value must be larger than 0. Given request limit is: " + requestLimit);
            }
            return new CrptApi(timeUnit, requestLimit);
        }
    }

    /**
     * Класс, представляющий документ.
     */
    public static class Document {
        private Description description;
        private String docId;
        private String docStatus;
        private String docType;
        private boolean importRequest;
        private String ownerInn;
        private String participantInn;
        private String producerInn;
        private String productionDate;
        private String productionType;
        private List<Product> products;
        private String regDate;
        private String regNumber;

        /**
         * Конструктор класса Document.
         *
         * @param description     Описание документа.
         * @param docId          Идентификатор документа.
         * @param docStatus      Статус документа.
         * @param docType        Тип документа.
         * @param importRequest   Флаг импортного запроса.
         * @param ownerInn       ИНН владельца.
         * @param participantInn ИНН участника.
         * @param producerInn    ИНН производителя.
         * @param productionDate Дата производства.
         * @param productionType Тип производства.
         * @param products        Список продуктов.
         * @param regDate        Дата регистрации.
         * @param regNumber      Регистрационный номер.
         */
        public Document(Description description, String docId,
                        String docStatus, String docType, boolean importRequest,
                        String ownerInn, String participantInn,
                        String producerInn, String productionDate,
                        String productionType, List<Product> products,
                        String regDate, String regNumber) {
            this.description = description;
            this.docId = docId;
            this.docStatus = docStatus;
            this.docType = docType;
            this.importRequest = importRequest;
            this.ownerInn = ownerInn;
            this.participantInn = participantInn;
            this.producerInn = producerInn;
            this.productionDate = productionDate;
            this.productionType = productionType;
            this.products = products;
            this.regDate = regDate;
            this.regNumber = regNumber;
        }

        /**
         * Пустой конструктор класса Document.
         */
        public Document() {
        }

        /**
         * Получает идентификатор документа.
         *
         * @return Идентификатор документа.
         */
        public String getDocId() {
            return docId;
        }

        /**
         * Устанавливает идентификатор документа.
         *
         * @param docId Идентификатор документа.
         */
        public void setDocId(String docId) {
            this.docId = docId;
        }

        /**
         * Получает статус документа.
         *
         * @return Статус документа.
         */
        public String getDocStatus() {
            return docStatus;
        }

        /**
         * Устанавливает статус документа.
         *
         * @param docStatus Статус документа.
         */
        public void setDocStatus(String docStatus) {
            this.docStatus = docStatus;
        }

        /**
         * Получает тип документа.
         *
         * @return Тип документа.
         */
        public String getDocType() {
            return docType;
        }

        /**
         * Устанавливает тип документа.
         *
         * @param docType Тип документа.
         */
        public void setDocType(String docType) {
            this.docType = docType;
        }

        /**
         * Проверяет, является ли документ импортным запросом.
         *
         * @return true, если документ является импортным запросом, иначе - false.
         */
        public boolean isImportRequest() {
            return importRequest;
        }

        /**
         * Устанавливает флаг импортного запроса.
         *
         * @param importRequest Флаг импортного запроса.
         */
        public void setImportRequest(boolean importRequest) {
            this.importRequest = importRequest;
        }

        /**
         * Получает ИНН владельца.
         *
         * @return ИНН владельца.
         */
        public String getOwnerInn() {
            return ownerInn;
        }

        /**
         * Устанавливает ИНН владельца.
         *
         * @param ownerInn ИНН владельца.
         */
        public void setOwnerInn(String ownerInn) {
            this.ownerInn = ownerInn;
        }

        /**
         * Получает ИНН участника.
         *
         * @return ИНН участника.
         */
        public String getParticipantInn() {
            return participantInn;
        }

        /**
         * Устанавливает ИНН участника.
         *
         * @param participantInn ИНН участника.
         */
        public void setParticipantInn(String participantInn) {
            this.participantInn = participantInn;
        }

        /**
         * Получает ИНН производителя.
         *
         * @return ИНН производителя.
         */
        public String getProducerInn() {
            return producerInn;
        }

        /**
         * Устанавливает ИНН производителя.
         *
         * @param producerInn ИНН производителя.
         */
        public void setProducerInn(String producerInn) {
            this.producerInn = producerInn;
        }

        /**
         * Получает дату производства.
         *
         * @return Дата производства.
         */
        public String getProductionDate() {
            return productionDate;
        }

        /**
         * Устанавливает дату производства.
         *
         * @param productionDate Дата производства.
         */
        public void setProductionDate(String productionDate) {
            this.productionDate = productionDate;
        }

        /**
         * Получает тип производства.
         *
         * @return Тип производства.
         */
        public String getProductionType() {
            return productionType;
        }

        /**
         * Устанавливает тип производства.
         *
         * @param productionType Тип производства.
         */
        public void setProductionType(String productionType) {
            this.productionType = productionType;
        }

        /**
         * Получает список продуктов.
         *
         * @return Список продуктов.
         */
        public List<Product> getProducts() {
            return products;
        }

        /**
         * Устанавливает список продуктов.
         *
         * @param products Список продуктов.
         */
        public void setProducts(List<Product> products) {
            this.products = products;
        }

        /**
         * Получает дату регистрации.
         *
         * @return Дата регистрации.
         */
        public String getRegDate() {
            return regDate;
        }

        /**
         * Устанавливает дату регистрации.
         *
         * @param regDate Дата регистрации.
         */
        public void setRegDate(String regDate) {
            this.regDate = regDate;
        }

        /**
         * Получает регистрационный номер.
         *
         * @return Регистрационный номер.
         */
        public String getRegNumber() {
            return regNumber;
        }

        /**
         * Устанавливает регистрационный номер.
         *
         * @param regNumber Регистрационный номер.
         */
        public void setRegNumber(String regNumber) {
            this.regNumber = regNumber;
        }

        /**
         * Возвращает строковое представление документа(номер)
         * @return строковое представление документа(номер)
         */
        @Override
        public String toString() {
            return "Document{" +
                    "doc_id='" + docId + '\'' +
                    '}';
        }
    }

    /**
     * Класс, представляющий описание документа.
     */
    static class Description {
        private String participantInn;

        /**
         * Получает ИНН участника.
         *
         * @return ИНН участника.
         */
        public String getParticipantInn() {
            return participantInn;
        }

        /**
         * Устанавливает ИНН участника.
         *
         * @param participantInn ИНН участника.
         */
        public void setParticipantInn(String participantInn) {
            this.participantInn = participantInn;
        }

        /**
         * Конструктор класса Description.
         *
         * @param participantInn ИНН участника.
         */
        public Description(String participantInn) {
            this.participantInn = participantInn;
        }
    }

    /**
     * Представляет продукт.
     */
    static class Product {

        /**
         * Создает новый экземпляр с указанными характеристиками.
         *
         * @param certificate Сертификат.
         * @param certificateDate Дата сертификата.
         * @param certificateNumber Номер сертификата.
         * @param ownerInn ИНН владельца.
         * @param producerInn ИНН производителя.
         * @param productionDate Дата производства.
         * @param tnvedCode Код ТН ВЭД.
         * @param uitCode Код УИТ.
         * @param uituCode Код УИТУ.
         */
        public Product(String certificate, String certificateDate,
                       String certificateNumber, String ownerInn,
                       String producerInn, String productionDate, String tnvedCode,
                       String uitCode, String uituCode) {
            this.certificateDocument = certificate;
            this.certificateDocumentDate = certificateDate;
            this.certificateDocumentNumber = certificateNumber;
            this.ownerInn = ownerInn;
            this.producerInn = producerInn;
            this.productionDate = productionDate;
            this.tnvedCode = tnvedCode;
            this.uitCode = uitCode;
            this.uituCode = uituCode;
        }

        private String certificateDocument;
        private String certificateDocumentDate;
        private String certificateDocumentNumber;
        private String ownerInn;
        private String producerInn;
        private String productionDate;
        private String tnvedCode;
        private String uitCode;
        private String uituCode;

        /**
         * Получает сертификат.
         *
         * @return Сертификат.
         */
        public String getCertificateDocument() {
            return certificateDocument;
        }

        /**
         * Устанавливает сертификат.
         *
         * @param certificateDocument Сертификат.
         */
        public void setCertificate_document(String certificateDocument) {
            this.certificateDocument = certificateDocument;
        }

        /**
         * Получает дату сертификата.
         *
         * @return Дата сертификата.
         */
        public String getCertificateDocumentDate() {
            return certificateDocumentDate;
        }

        /**
         * Устанавливает дату сертификата.
         *
         * @param certificateDocumentDate Дата сертификата.
         */
        public void setCertificateDocumentDate(String certificateDocumentDate) {
            this.certificateDocumentDate = certificateDocumentDate;
        }

        /**
         * Получает номер сертификата.
         *
         * @return Номер сертификата.
         */
        public String getCertificateDocumentNumber() {
            return certificateDocumentNumber;
        }

        /**
         * Устанавливает номер сертификата.
         *
         * @param certificateDocumentNumber Номер сертификата.
         */
        public void setCertificateDocumentNumber(String certificateDocumentNumber) {
            this.certificateDocumentNumber = certificateDocumentNumber;
        }

        /**
         * Получает ИНН владельца.
         *
         * @return ИНН владельца.
         */
        public String getOwnerInn() {
            return ownerInn;
        }

        /**
         * Устанавливает ИНН владельца.
         *
         * @param ownerInn ИНН владельца.
         */
        public void setOwnerInn(String ownerInn) {
            this.ownerInn = ownerInn;
        }

        /**
         * Получает ИНН производителя.
         *
         * @return ИНН производителя.
         */
        public String getProducerInn() {
            return producerInn;
        }

        /**
         * Устанавливает ИНН производителя.
         *
         * @param producerInn ИНН производителя.
         */
        public void setProducerInn(String producerInn) {
            this.producerInn = producerInn;
        }

        /**
         * Получает дату производства.
         *
         * @return Дата производства.
         */
        public String getProductionDate() {
            return productionDate;
        }

        /**
         * Устанавливает дату производства.
         *
         * @param productionDate Дата производства.
         */
        public void setProductionDate(String productionDate) {
            this.productionDate = productionDate;
        }

        /**
         * Получает код ТН ВЭД.
         *
         * @return Код ТН ВЭД.
         */
        public String getTnvedCode() {
            return tnvedCode;
        }

        /**
         * Устанавливает код ТН ВЭД.
         *
         * @param tnvedCode Код ТН ВЭД.
         */
        public void setTnvedCode(String tnvedCode) {
            this.tnvedCode = tnvedCode;
        }

        /**
         * Получает код УИТ.
         *
         * @return Код УИТ.
         */
        public String getUitCode() {
            return uitCode;
        }

        /**
         * Устанавливает код УИТ.
         *
         * @param uitCode Код УИТ.
         */
        public void setUitCode(String uitCode) {
            this.uitCode = uitCode;
        }

        /**
         * Получает код УИТУ.
         *
         * @return Код УИТУ.
         */
        public String getUituCode() {
            return uituCode;
        }

        /**
         * Устанавливает код УИТУ.
         *
         * @param uituCode Код УИТУ.
         */
        public void setUituCode(String uituCode) {
            this.uituCode = uituCode;
        }
    }
}