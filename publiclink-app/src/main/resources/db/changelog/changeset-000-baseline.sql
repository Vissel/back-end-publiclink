-- Changeset 000: baseline DDL for publiclink-db
-- Creates the base tables that changesets 001-009 used to create (those changesets
-- exist as files but are commented out of db.changelog-master.xml, so a fresh
-- database had no tables at all and changeset 010 failed with
-- "Table 'publiclink-db.order' doesn't exist").
--
-- Columns added by later changesets still enabled in the master are deliberately
-- NOT created here, so those ALTERs keep working:
--   `order`   -> amount, unit, note            (changeset 010)
--   user      -> has_authentication            (dropped by changeset 014)
--   picture   -> data                          (changeset 015)
--   sale_environment -> will_ended_at          (changeset 016)
--   request   -> start_time, end_time          (changeset 020)
--
-- All statements use IF NOT EXISTS so this is safe on a partially created schema.

CREATE TABLE IF NOT EXISTS `user` (
	user_id varchar(50) primary key,
	user_name varchar(50),
	temp_password varchar(255),
	name varchar(50),
	link varchar(250),
	type varchar(50),
	has_authentication tinyint(1) default 0,
	created_at timestamp default current_timestamp,
	unique key unique_usernamelink (user_name, link)
);

CREATE TABLE IF NOT EXISTS request (
	req_id bigint primary key auto_increment,
	seller_id varchar(50),
	seller_name varchar(50),
	description varchar(250),
	user_name varchar(50),
	created_by varchar(50),
	authenticated bit(1) default 0,
	req_uuid varchar(36),
	req_auth_link varchar(255),
	created_at timestamp default current_timestamp,
	constraint fk_request_created_by foreign key (created_by) references `user`(user_id)
);

CREATE TABLE IF NOT EXISTS product (
	product_id bigint primary key auto_increment,
	product_name varchar(250) not null,
	amount int,
	unit varchar(5),
	price double,
	total_amount int,
	req_id bigint,
	constraint fk_product_request foreign key (req_id) references request(req_id)
);

CREATE TABLE IF NOT EXISTS picture (
	pic_id bigint primary key auto_increment,
	link varchar(255) not null,
	title varchar(50)
);

CREATE TABLE IF NOT EXISTS product_picture_map (
	map_id bigint primary key auto_increment,
	product_id bigint not null,
	pic_id bigint,
	constraint fk_ppm_product foreign key (product_id) references product(product_id),
	constraint fk_ppm_picture foreign key (pic_id) references picture(pic_id)
);

CREATE TABLE IF NOT EXISTS payment_method (
	method_id int primary key auto_increment,
	method_name varchar(50),
	method_info varchar(50)
);

CREATE TABLE IF NOT EXISTS sale_environment (
	env_id varchar(50) primary key,
	req_id bigint not null,
	public_link varchar(255) not null,
	state tinyint default 1,
	created_at timestamp default current_timestamp,
	ended_at timestamp null,
	constraint fk_env_request foreign key (req_id) references request(req_id)
);

CREATE TABLE IF NOT EXISTS `order` (
	order_id bigint primary key auto_increment,
	buyer_name varchar(250) not null,
	env_id varchar(50) not null,
	ordered_at timestamp default current_timestamp,
	method_id int null,
	seller_note varchar(250) null,
	delivered tinyint default 0,
	get_money tinyint default 0,
	constraint fk_order_env foreign key (env_id) references sale_environment(env_id),
	constraint fk_order_method foreign key (method_id) references payment_method(method_id)
);

CREATE TABLE IF NOT EXISTS process_message (
	msg_id varchar(255) primary key,
	created_at timestamp(6) null,
	queue_name varchar(255),
	status varchar(255)
);
