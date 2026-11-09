use `publiclink-db`;

CREATE TABLE IF NOT EXISTS product (
	product_id bigint primary key auto_increment,
	product_name varchar(250) not null,
    amount int,
    unit varchar(5),
    price double,
    total_amount int
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
    foreign key (product_id) references product(product_id),
    foreign key (pic_id) references picture(pic_id)
);
