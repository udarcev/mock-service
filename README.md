Это проект мок сервиса для нужд тестирования.
В самом тестируемом приложении эндпоинты внешних систем меняются на адрес мок сервиса.
Далее, у этого сервиса есть 2 типа работы
- Заранее сконфигурированные ответы на типовые запросы, с простейшей подстановкой переменных из запроса в ответ.
- Динамическое добавление одноразовых моков для нетипичных кейсов.

Как это работает? Тестировщик, который пишет автотест, запускает некоторый процесс в тестируемой системе. Если при этом процессе есть вызовы внешних систем, и он знает об этих вызовах,
он может прямо в коде автотеста отправить в сервис заглушек правильный настроенный ответ на запрос, который система будет отправлять.

