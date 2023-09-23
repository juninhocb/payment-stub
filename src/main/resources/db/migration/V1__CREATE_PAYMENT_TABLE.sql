drop table if exists payment;
create table payment (
    amount decimal(38,2) not null,
    payment_number integer not null,
    timestamp datetime(6) not null,
    id varchar(36) not null,
    payer VARCHAR(100) not null,
    primary key (id)
) engine=InnoDB;
alter table payment add constraint UK_k8ietisjrevcrs31m339ms6v3 unique (payment_number);