# ${md:README}

---
{% if tag:review %}
# Измененный исходный код LG IntelliJ Platform Plugin в текущей ветке
{% else %}
# Исходный код LG IntelliJ Platform Plugin
{% endif %}

${src}

---

# IntelliJ Platform — Документация для разработки плагинов

> Показаны только главы, нужные для разработки на текущей фазе.

${md@self:intellij-platform-docs/14-persistence.md}

${md@self:intellij-platform-docs/07-kotlin-ui-dsl.md}

---

${md@self:architecture}

---

${md@self:plan-mini}
{% if task AND scope:local %}
---

# Описание текущей задачи

${task}{% endif %}