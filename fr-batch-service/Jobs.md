# Jobs ERD

```
//// Created un dbdiagram.io

Enum jobs_status {
  enabled  [note: 'job must run when triggered']
  disabled [note: 'job not must run when triggered']
  archived [note: 'job is marked for future deletion']

}

Enum countries {
  MX  [note: 'Mexico']
  USA [note: 'United States of America']
}

// Spring jobs available
Table jobs as J {
  id int [pk, increment] // auto-increment
  bean_name      varchar     [note: ' spring bean name used to create an instance']
  description    varchar     [note: 'what is this job?']
  created_by     varchar
  last_update_by varchar
  created_at     timestamp   [default: `now()`]
}

// Job definition, based on bussiness
Table job_definitions as JD {
  id int [pk, increment] // auto-increment
  job_id int [ref: > J.id]
  runnable_for_past_days boolean [note: 'if this job can be runned for past days']
  created_by     varchar
  country        countries
  status         jobs_status
  created_at     timestamp   [default: `now()`]
}

Table job_params as JP {
  id     int     [pk, increment]
  job_id int     [ref: > job_definitions.id]
  name   varchar
  value  varchar
 }
```