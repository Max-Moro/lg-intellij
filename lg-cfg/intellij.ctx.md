{# ${md:README}

---
{% if tag:review %}
# Измененный исходный код LG IntelliJ Platform Plugin в текущей ветке
{% else %}
# Исходный код LG IntelliJ Platform Plugin
{% endif %}

${src}
#}{% if tag:docs %}
---

# IntelliJ Platform — Документация для разработки плагинов

${md@self:intellij-platform-docs/*}
{% endif %} {% if task AND scope:local %}
---

# Описание текущей задачи

${task}{% endif %}