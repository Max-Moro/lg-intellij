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

${md@self:intellij-platform-docs/08-actions.md}

${md@self:intellij-platform-docs/09-vfs.md}

${md@self:intellij-platform-docs/17-tree-views.md}

---

${md@self:architecture}

---

${md@self:plan-mini}
{% if task AND scope:local %}
---

# Описание текущей задачи

${task}{% endif %}