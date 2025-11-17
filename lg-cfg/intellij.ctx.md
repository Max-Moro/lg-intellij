{% if scope:local AND tag:agent %}
${tpl:agent/index}

---
{% endif %}
${md:README}

---
{% if tag:review %}
# Modified Source Code of LG IntelliJ Platform Plugin in Current Branch
{% else %}
# Source Code of LG IntelliJ Platform Plugin
{% endif %}

${src}
{% if task AND scope:local %}
---

# Description of Current Task

${task}{% endif %}
{% if scope:local AND tag:agent %}
${tpl:agent/footer}
{% endif %}