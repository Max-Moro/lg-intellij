{% if scope:local AND tag:agent %}
${tpl:agent/index}

---
{% endif %}
${md:README}

---
{% if tag:review %}
# Измененный исходный код LG IntelliJ Platform Plugin в текущей ветке
{% else %}
# Исходный код LG IntelliJ Platform Plugin
{% endif %}

${src}
{% if task AND scope:local %}
---

# Описание текущей задачи

${task}{% endif %}
{% if scope:local AND tag:agent %}
${tpl:agent/footer}
{% endif %}