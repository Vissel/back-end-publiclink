alter table request
add column req_uuid varchar(36) null,
add column req_auth_link varchar(255) null;

update request
set req_uuid = uuid()
where req_uuid is null;

alter table request
modify column req_uuid varchar(36) not null;

alter table request
add constraint unique_request_req_uuid unique (req_uuid);

alter table request
drop foreign key user_id;
column seller_id;

alter table request
add column seller_name varchar(50) null,