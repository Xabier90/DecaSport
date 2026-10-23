--
-- PostgreSQL database dump
--

\restrict QOoXCoPfD2GjakBiSrNnFSmLD4avssvnRHS5JwofK5uyb0pqhGGRmP1vh4pMUpt

-- Dumped from database version 18.6 (Debian 18.6-1.pgdg13+2)
-- Dumped by pg_dump version 18.6 (Debian 18.6-1.pgdg13+2)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: clientes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.clientes (
    id_cliente integer NOT NULL,
    id_usuario integer,
    nombre character varying(100) NOT NULL,
    email character varying(100),
    telefono character varying(20),
    pais character varying(50)
);


--
-- Name: clientes_id_cliente_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.clientes_id_cliente_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: clientes_id_cliente_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.clientes_id_cliente_seq OWNED BY public.clientes.id_cliente;


--
-- Name: componentes_fabricacion; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.componentes_fabricacion (
    id_componente integer CONSTRAINT componentes_fabricacion_id_componente_not_null1 NOT NULL,
    id_producto integer NOT NULL,
    nombre character varying(100) NOT NULL,
    variante character varying(50),
    cantidad_necesaria integer DEFAULT 1 NOT NULL,
    stock_actual integer DEFAULT 0 NOT NULL,
    stock_minimo integer DEFAULT 0 NOT NULL,
    id_proveedor integer,
    CONSTRAINT componentes_fabricacion_cantidad_necesaria_check CHECK ((cantidad_necesaria > 0)),
    CONSTRAINT componentes_fabricacion_stock_actual_check CHECK ((stock_actual >= 0)),
    CONSTRAINT componentes_fabricacion_stock_minimo_check CHECK ((stock_minimo >= 0))
);


--
-- Name: componentes_fabricacion_antigua; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.componentes_fabricacion_antigua (
    id_componente integer CONSTRAINT componentes_fabricacion_id_componente_not_null NOT NULL,
    deporte character varying(20) CONSTRAINT componentes_fabricacion_deporte_not_null NOT NULL,
    tipo_componente character varying(30) CONSTRAINT componentes_fabricacion_tipo_componente_not_null NOT NULL,
    modelo character varying(50) CONSTRAINT componentes_fabricacion_modelo_not_null NOT NULL,
    marca character varying(50),
    talla character varying(20),
    longitud_cm integer,
    almacen character varying(20) DEFAULT 'España'::character varying CONSTRAINT componentes_fabricacion_almacen_not_null NOT NULL,
    cantidad integer DEFAULT 0 CONSTRAINT componentes_fabricacion_cantidad_not_null NOT NULL,
    id_proveedor integer,
    CONSTRAINT componentes_fabricacion_almacen_check CHECK (((almacen)::text = ANY ((ARRAY['España'::character varying, 'Francia'::character varying])::text[]))),
    CONSTRAINT componentes_fabricacion_cantidad_check CHECK ((cantidad >= 0)),
    CONSTRAINT componentes_fabricacion_deporte_check CHECK (((deporte)::text = ANY ((ARRAY['bici'::character varying, 'snowboard'::character varying])::text[]))),
    CONSTRAINT componentes_fabricacion_longitud_cm_check CHECK ((longitud_cm > 0)),
    CONSTRAINT componentes_fabricacion_tipo_componente_check CHECK (((tipo_componente)::text = ANY ((ARRAY['manillar'::character varying, 'cuadro'::character varying, 'ruedas'::character varying, 'tabla'::character varying, 'fijaciones'::character varying])::text[])))
);


--
-- Name: componentes_fabricacion_id_componente_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.componentes_fabricacion_id_componente_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: componentes_fabricacion_id_componente_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.componentes_fabricacion_id_componente_seq OWNED BY public.componentes_fabricacion_antigua.id_componente;


--
-- Name: componentes_fabricacion_id_componente_seq1; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.componentes_fabricacion_id_componente_seq1
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: componentes_fabricacion_id_componente_seq1; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.componentes_fabricacion_id_componente_seq1 OWNED BY public.componentes_fabricacion.id_componente;


--
-- Name: compras; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.compras (
    id_compra integer NOT NULL,
    id_proveedor integer NOT NULL,
    id_producto integer NOT NULL,
    cantidad integer NOT NULL,
    fecha date DEFAULT CURRENT_DATE NOT NULL,
    estado character varying(20) DEFAULT 'Pendiente'::character varying,
    id_componente integer,
    CONSTRAINT compras_cantidad_check CHECK ((cantidad > 0))
);


--
-- Name: compras_componentes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.compras_componentes (
    id_compra_componente integer NOT NULL,
    id_proveedor integer NOT NULL,
    id_componente integer NOT NULL,
    cantidad integer NOT NULL,
    precio_unidad numeric(10,2),
    fecha date DEFAULT CURRENT_DATE NOT NULL,
    CONSTRAINT compras_componentes_cantidad_check CHECK ((cantidad > 0)),
    CONSTRAINT compras_componentes_precio_unidad_check CHECK ((precio_unidad >= (0)::numeric))
);


--
-- Name: compras_componentes_id_compra_componente_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.compras_componentes_id_compra_componente_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: compras_componentes_id_compra_componente_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.compras_componentes_id_compra_componente_seq OWNED BY public.compras_componentes.id_compra_componente;


--
-- Name: compras_id_compra_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.compras_id_compra_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: compras_id_compra_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.compras_id_compra_seq OWNED BY public.compras.id_compra;


--
-- Name: fabricacion; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.fabricacion (
    id_montaje integer CONSTRAINT montajes_id_montaje_not_null NOT NULL,
    id_pedido integer CONSTRAINT montajes_id_pedido_not_null NOT NULL,
    id_producto integer CONSTRAINT montajes_id_producto_not_null NOT NULL,
    tipo character varying(10) CONSTRAINT montajes_tipo_not_null NOT NULL,
    id_usuario integer,
    fecha date DEFAULT CURRENT_DATE CONSTRAINT montajes_fecha_not_null NOT NULL,
    estado character varying(20) DEFAULT 'pendiente'::character varying CONSTRAINT montajes_estado_not_null NOT NULL,
    CONSTRAINT montajes_tipo_check CHECK (((tipo)::text = ANY (ARRAY[('snow'::character varying)::text, ('bici'::character varying)::text])))
);


--
-- Name: facturas; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.facturas (
    id_factura integer NOT NULL,
    id_pedido integer NOT NULL,
    fecha date DEFAULT CURRENT_DATE NOT NULL,
    total numeric(10,2) NOT NULL
);


--
-- Name: facturas_id_factura_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.facturas_id_factura_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: facturas_id_factura_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.facturas_id_factura_seq OWNED BY public.facturas.id_factura;


--
-- Name: lineas_pedido; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.lineas_pedido (
    id_linea integer NOT NULL,
    id_pedido integer NOT NULL,
    id_producto integer NOT NULL,
    cantidad integer NOT NULL,
    CONSTRAINT lineas_pedido_cantidad_check CHECK ((cantidad > 0))
);


--
-- Name: lineas_pedido_id_linea_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.lineas_pedido_id_linea_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: lineas_pedido_id_linea_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.lineas_pedido_id_linea_seq OWNED BY public.lineas_pedido.id_linea;


--
-- Name: montaje_componentes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.montaje_componentes (
    id_montaje_componente integer NOT NULL,
    id_montaje integer NOT NULL,
    id_componente integer NOT NULL,
    cantidad integer NOT NULL,
    CONSTRAINT montaje_componentes_cantidad_check CHECK ((cantidad > 0))
);


--
-- Name: montaje_componentes_id_montaje_componente_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.montaje_componentes_id_montaje_componente_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: montaje_componentes_id_montaje_componente_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.montaje_componentes_id_montaje_componente_seq OWNED BY public.montaje_componentes.id_montaje_componente;


--
-- Name: montajes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.montajes (
    id_montaje integer CONSTRAINT montajes_id_montaje_not_null1 NOT NULL,
    id_pedido integer,
    id_producto integer CONSTRAINT montajes_id_producto_not_null1 NOT NULL,
    tipo character varying(10) CONSTRAINT montajes_tipo_not_null1 NOT NULL,
    id_usuario integer NOT NULL,
    fecha date DEFAULT CURRENT_DATE CONSTRAINT montajes_fecha_not_null1 NOT NULL,
    estado character varying(20) DEFAULT 'pendiente'::character varying CONSTRAINT montajes_estado_not_null1 NOT NULL,
    CONSTRAINT montajes_estado_check CHECK (((estado)::text = ANY ((ARRAY['pendiente'::character varying, 'en proceso'::character varying, 'terminado'::character varying])::text[]))),
    CONSTRAINT montajes_tipo_check1 CHECK (((tipo)::text = ANY ((ARRAY['bici'::character varying, 'snowboard'::character varying])::text[])))
);


--
-- Name: montajes_id_montaje_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.montajes_id_montaje_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: montajes_id_montaje_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.montajes_id_montaje_seq OWNED BY public.fabricacion.id_montaje;


--
-- Name: montajes_id_montaje_seq1; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.montajes_id_montaje_seq1
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: montajes_id_montaje_seq1; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.montajes_id_montaje_seq1 OWNED BY public.montajes.id_montaje;


--
-- Name: oportunidades; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.oportunidades (
    id_oportunidad integer NOT NULL,
    id_cliente integer NOT NULL,
    id_producto integer NOT NULL,
    cantidad integer NOT NULL,
    id_usuario integer,
    fecha date DEFAULT CURRENT_DATE NOT NULL,
    estado character varying(20) DEFAULT 'Abierta'::character varying NOT NULL,
    CONSTRAINT oportunidades_cantidad_check CHECK ((cantidad > 0))
);


--
-- Name: oportunidades_id_oportunidad_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.oportunidades_id_oportunidad_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: oportunidades_id_oportunidad_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.oportunidades_id_oportunidad_seq OWNED BY public.oportunidades.id_oportunidad;


--
-- Name: pedidos; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pedidos (
    id_pedido integer NOT NULL,
    id_cliente integer NOT NULL,
    fecha date DEFAULT CURRENT_DATE NOT NULL,
    estado character varying(20) DEFAULT 'pendiente'::character varying NOT NULL
);


--
-- Name: pedidos_id_pedido_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.pedidos_id_pedido_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pedidos_id_pedido_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.pedidos_id_pedido_seq OWNED BY public.pedidos.id_pedido;


--
-- Name: productos; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.productos (
    id_producto integer NOT NULL,
    nombre character varying(100) NOT NULL,
    categoria character varying(50),
    precio numeric(10,2) NOT NULL,
    id_proveedor integer
);


--
-- Name: productos_id_producto_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.productos_id_producto_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: productos_id_producto_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.productos_id_producto_seq OWNED BY public.productos.id_producto;


--
-- Name: proveedores; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.proveedores (
    id_proveedor integer NOT NULL,
    nombre character varying(100) NOT NULL,
    email character varying(100),
    telefono character varying(20)
);


--
-- Name: proveedores_id_proveedor_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.proveedores_id_proveedor_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: proveedores_id_proveedor_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.proveedores_id_proveedor_seq OWNED BY public.proveedores.id_proveedor;


--
-- Name: roles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.roles (
    id_rol integer NOT NULL,
    nombre_rol character varying(30) NOT NULL
);


--
-- Name: roles_id_rol_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.roles_id_rol_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: roles_id_rol_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.roles_id_rol_seq OWNED BY public.roles.id_rol;


--
-- Name: stock; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.stock (
    id_stock integer NOT NULL,
    id_producto integer NOT NULL,
    almacen character varying(20) NOT NULL,
    cantidad integer DEFAULT 0 NOT NULL,
    CONSTRAINT stock_cantidad_check CHECK ((cantidad >= 0))
);


--
-- Name: stock_id_stock_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.stock_id_stock_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: stock_id_stock_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.stock_id_stock_seq OWNED BY public.stock.id_stock;


--
-- Name: usuarios; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.usuarios (
    id_usuario integer NOT NULL,
    nombre_usuario character varying(50) NOT NULL,
    contrasena character varying(100) NOT NULL,
    id_rol integer NOT NULL
);


--
-- Name: usuarios_id_usuario_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.usuarios_id_usuario_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: usuarios_id_usuario_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.usuarios_id_usuario_seq OWNED BY public.usuarios.id_usuario;


--
-- Name: clientes id_cliente; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.clientes ALTER COLUMN id_cliente SET DEFAULT nextval('public.clientes_id_cliente_seq'::regclass);


--
-- Name: componentes_fabricacion id_componente; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.componentes_fabricacion ALTER COLUMN id_componente SET DEFAULT nextval('public.componentes_fabricacion_id_componente_seq1'::regclass);


--
-- Name: componentes_fabricacion_antigua id_componente; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.componentes_fabricacion_antigua ALTER COLUMN id_componente SET DEFAULT nextval('public.componentes_fabricacion_id_componente_seq'::regclass);


--
-- Name: compras id_compra; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.compras ALTER COLUMN id_compra SET DEFAULT nextval('public.compras_id_compra_seq'::regclass);


--
-- Name: compras_componentes id_compra_componente; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.compras_componentes ALTER COLUMN id_compra_componente SET DEFAULT nextval('public.compras_componentes_id_compra_componente_seq'::regclass);


--
-- Name: fabricacion id_montaje; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fabricacion ALTER COLUMN id_montaje SET DEFAULT nextval('public.montajes_id_montaje_seq'::regclass);


--
-- Name: facturas id_factura; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.facturas ALTER COLUMN id_factura SET DEFAULT nextval('public.facturas_id_factura_seq'::regclass);


--
-- Name: lineas_pedido id_linea; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lineas_pedido ALTER COLUMN id_linea SET DEFAULT nextval('public.lineas_pedido_id_linea_seq'::regclass);


--
-- Name: montaje_componentes id_montaje_componente; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.montaje_componentes ALTER COLUMN id_montaje_componente SET DEFAULT nextval('public.montaje_componentes_id_montaje_componente_seq'::regclass);


--
-- Name: montajes id_montaje; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.montajes ALTER COLUMN id_montaje SET DEFAULT nextval('public.montajes_id_montaje_seq1'::regclass);


--
-- Name: oportunidades id_oportunidad; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.oportunidades ALTER COLUMN id_oportunidad SET DEFAULT nextval('public.oportunidades_id_oportunidad_seq'::regclass);


--
-- Name: pedidos id_pedido; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pedidos ALTER COLUMN id_pedido SET DEFAULT nextval('public.pedidos_id_pedido_seq'::regclass);


--
-- Name: productos id_producto; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.productos ALTER COLUMN id_producto SET DEFAULT nextval('public.productos_id_producto_seq'::regclass);


--
-- Name: proveedores id_proveedor; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.proveedores ALTER COLUMN id_proveedor SET DEFAULT nextval('public.proveedores_id_proveedor_seq'::regclass);


--
-- Name: roles id_rol; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.roles ALTER COLUMN id_rol SET DEFAULT nextval('public.roles_id_rol_seq'::regclass);


--
-- Name: stock id_stock; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stock ALTER COLUMN id_stock SET DEFAULT nextval('public.stock_id_stock_seq'::regclass);


--
-- Name: usuarios id_usuario; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuarios ALTER COLUMN id_usuario SET DEFAULT nextval('public.usuarios_id_usuario_seq'::regclass);


--
-- Data for Name: clientes; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.clientes (id_cliente, id_usuario, nombre, email, telefono, pais) FROM stdin;
6	24	Hugo Rayo	hugorayo@gmail.com	\N	\N
7	28	hola	hola@gmail.com	\N	\N
\.


--
-- Data for Name: componentes_fabricacion; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.componentes_fabricacion (id_componente, id_producto, nombre, variante, cantidad_necesaria, stock_actual, stock_minimo, id_proveedor) FROM stdin;
1	8	casco	\N	2	3	2	1
\.


--
-- Data for Name: componentes_fabricacion_antigua; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.componentes_fabricacion_antigua (id_componente, deporte, tipo_componente, modelo, marca, talla, longitud_cm, almacen, cantidad, id_proveedor) FROM stdin;
\.


--
-- Data for Name: compras; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.compras (id_compra, id_proveedor, id_producto, cantidad, fecha, estado, id_componente) FROM stdin;
\.


--
-- Data for Name: compras_componentes; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.compras_componentes (id_compra_componente, id_proveedor, id_componente, cantidad, precio_unidad, fecha) FROM stdin;
\.


--
-- Data for Name: fabricacion; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.fabricacion (id_montaje, id_pedido, id_producto, tipo, id_usuario, fecha, estado) FROM stdin;
\.


--
-- Data for Name: facturas; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.facturas (id_factura, id_pedido, fecha, total) FROM stdin;
\.


--
-- Data for Name: lineas_pedido; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.lineas_pedido (id_linea, id_pedido, id_producto, cantidad) FROM stdin;
1	1	20	3
2	1	25	1
\.


--
-- Data for Name: montaje_componentes; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.montaje_componentes (id_montaje_componente, id_montaje, id_componente, cantidad) FROM stdin;
\.


--
-- Data for Name: montajes; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.montajes (id_montaje, id_pedido, id_producto, tipo, id_usuario, fecha, estado) FROM stdin;
\.


--
-- Data for Name: oportunidades; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.oportunidades (id_oportunidad, id_cliente, id_producto, cantidad, id_usuario, fecha, estado) FROM stdin;
\.


--
-- Data for Name: pedidos; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.pedidos (id_pedido, id_cliente, fecha, estado) FROM stdin;
1	7	2026-10-22	pendiente
\.


--
-- Data for Name: productos; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.productos (id_producto, nombre, categoria, precio, id_proveedor) FROM stdin;
1	Bici carretera Aero	Bicicleta	1299.00	1
2	Bici montaña Trail 29	Bicicleta	949.00	1
3	Bici urbana City	Bicicleta	549.00	1
4	Bici gravel Explorer	Bicicleta	1099.00	2
5	Tabla snowboard All Mountain	Snowboard	399.00	3
6	Tabla snowboard Freestyle	Snowboard	349.00	3
7	Tabla snowboard Freeride	Snowboard	459.00	3
8	Casco ciclismo	Accesorios	59.90	2
9	Casco snowboard	Accesorios	79.90	3
10	Guantes snowboard	Accesorios	34.50	3
11	Balón fútbol talla 5	Fútbol	24.90	4
12	Botas fútbol césped	Fútbol	69.90	4
13	Espinilleras	Fútbol	12.50	4
14	Guantes portero	Fútbol	29.90	4
15	Balón baloncesto talla 7	Baloncesto	29.90	4
16	Zapatillas baloncesto	Baloncesto	89.90	4
17	Raqueta tenis	Tenis	79.00	4
18	Bote pelotas tenis (3 uds)	Tenis	5.90	4
19	Pala pádel	Pádel	99.00	4
20	Zapatillas running	Running	59.90	5
21	Camiseta técnica running	Running	14.90	5
22	Gafas natación	Natación	9.90	5
23	Bañador competición	Natación	24.90	5
24	Mochila trekking 40 L	Montaña	89.00	5
25	Botas montaña	Montaña	109.00	5
26	Tabla surf 7 pies	Surf	299.00	5
27	Neopreno surf 4/3	Surf	149.00	5
28	Esterilla yoga	Fitness	19.90	5
29	Mancuernas 2 x 5 kg	Fitness	34.90	5
\.


--
-- Data for Name: proveedores; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.proveedores (id_proveedor, nombre, email, telefono) FROM stdin;
1	Bicis Norte S.L.	pedidos@bicisnorte.example	943100200
2	Componentes Ciclo S.A.	ventas@componentesciclo.example	943300400
3	Snow Pyrenees S.L.	info@snowpyrenees.example	948500600
4	Deportes Balón S.L.	pedidos@deportesbalon.example	944200300
5	Outdoor Gipuzkoa S.A.	ventas@outdoorgipuzkoa.example	943700800
\.


--
-- Data for Name: roles; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.roles (id_rol, nombre_rol) FROM stdin;
1	administrador
2	trabajador
3	cliente
4	comercial
5	crm
6	ventas
7	compras
8	inventario
9	fabricacion
\.


--
-- Data for Name: stock; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.stock (id_stock, id_producto, almacen, cantidad) FROM stdin;
1	1	Francia	2
2	1	España	4
3	2	España	6
4	3	España	10
5	4	Francia	3
6	5	Francia	4
7	5	España	5
8	6	España	7
9	7	Francia	2
10	8	España	25
11	9	España	15
12	10	Francia	30
13	11	Francia	20
14	11	España	40
15	12	España	18
16	13	España	35
17	14	Francia	12
18	15	España	25
19	16	España	10
20	17	Francia	8
21	18	España	60
22	19	España	14
23	20	Francia	10
25	21	España	50
26	22	España	45
27	23	Francia	20
28	24	España	9
30	26	España	5
31	27	España	12
32	28	España	30
33	29	Francia	16
24	20	España	19
29	25	Francia	10
\.


--
-- Data for Name: usuarios; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.usuarios (id_usuario, nombre_usuario, contrasena, id_rol) FROM stdin;
1	admin	240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9	1
2	ibon	29602954872a4db1d225f838293fccf59ce5f5a94be2b6ad5a0a62435fd260ef	1
3	juan	f6ccb3e8d609012238c0b39e60b2c9632b3cdede91e035dad1de43469768f4cc	1
4	aaron	426bbac10d78117269526f165ecded2f27bb85e1eed5607510d11c563f464995	1
5	aroa	af1c183c05f0568c58d983b8d91211ae9a95f371e421677bf9a04ad04edbada2	1
6	xabi	2ed4bf7d28bd68bbc5981634ed177c8dced07433de123b8599ede4f4e4c77898	1
7	comercial	e7fe30c4c1efcaeeacfc35ffbebdc95989cea70c1a9867e57f23edd87f0f89e8	4
8	ventas	e2151232843fc5ee75d0c8bfc0c74bdcdace97b001a7dab658f7a27ecc8d93f5	2
9	almacenero	4e3adda00def40bed130aeb68063761a29f10a247a2c6d7be4c9f06c2a16314d	2
10	operario	ea66d0295003394ff7f847590fe43af491ded28bb3837d543d55dfa130c9b2f3	2
11	compras	60a48f71a53d8dc2abffec0545e2eb3c1d59ad0e95cce534cfe12e44582fb5eb	2
24	Hugotukituki	0478721f1106c2a631a90181bac7efc77767a3903eb9220687bff8a14e940fa7	3
25	crm	72b9c07e35512213625383e02bae466ce08a591df5cd6de8bd9d8ecbdb07ce17	5
26	inventario	f54568eeb40e6872d8df7afffc13608decaee72e20803a0da71309ece6cc73ea	8
27	fabricacion	eae35b1cadf1a8cd37bb1a22ee9dbd9b1f7034e3cbf7e724dea863db54083d87	9
28	Hola	b221d9dbb083a7f33428d7c2a3c3198ae925614d70210e28716ccaa7cd4ddb79	3
\.


--
-- Name: clientes_id_cliente_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.clientes_id_cliente_seq', 7, true);


--
-- Name: componentes_fabricacion_id_componente_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.componentes_fabricacion_id_componente_seq', 1, false);


--
-- Name: componentes_fabricacion_id_componente_seq1; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.componentes_fabricacion_id_componente_seq1', 1, true);


--
-- Name: compras_componentes_id_compra_componente_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.compras_componentes_id_compra_componente_seq', 1, false);


--
-- Name: compras_id_compra_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.compras_id_compra_seq', 1, false);


--
-- Name: facturas_id_factura_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.facturas_id_factura_seq', 1, false);


--
-- Name: lineas_pedido_id_linea_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.lineas_pedido_id_linea_seq', 2, true);


--
-- Name: montaje_componentes_id_montaje_componente_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.montaje_componentes_id_montaje_componente_seq', 1, false);


--
-- Name: montajes_id_montaje_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.montajes_id_montaje_seq', 1, false);


--
-- Name: montajes_id_montaje_seq1; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.montajes_id_montaje_seq1', 1, false);


--
-- Name: oportunidades_id_oportunidad_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.oportunidades_id_oportunidad_seq', 1, false);


--
-- Name: pedidos_id_pedido_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.pedidos_id_pedido_seq', 1, true);


--
-- Name: productos_id_producto_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.productos_id_producto_seq', 30, true);


--
-- Name: proveedores_id_proveedor_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.proveedores_id_proveedor_seq', 5, true);


--
-- Name: roles_id_rol_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.roles_id_rol_seq', 9, true);


--
-- Name: stock_id_stock_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.stock_id_stock_seq', 34, true);


--
-- Name: usuarios_id_usuario_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.usuarios_id_usuario_seq', 28, true);


--
-- Name: clientes clientes_id_usuario_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.clientes
    ADD CONSTRAINT clientes_id_usuario_key UNIQUE (id_usuario);


--
-- Name: clientes clientes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.clientes
    ADD CONSTRAINT clientes_pkey PRIMARY KEY (id_cliente);


--
-- Name: componentes_fabricacion_antigua componentes_fabricacion_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.componentes_fabricacion_antigua
    ADD CONSTRAINT componentes_fabricacion_pkey PRIMARY KEY (id_componente);


--
-- Name: componentes_fabricacion componentes_fabricacion_pkey1; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.componentes_fabricacion
    ADD CONSTRAINT componentes_fabricacion_pkey1 PRIMARY KEY (id_componente);


--
-- Name: compras_componentes compras_componentes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.compras_componentes
    ADD CONSTRAINT compras_componentes_pkey PRIMARY KEY (id_compra_componente);


--
-- Name: compras compras_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.compras
    ADD CONSTRAINT compras_pkey PRIMARY KEY (id_compra);


--
-- Name: facturas facturas_id_pedido_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.facturas
    ADD CONSTRAINT facturas_id_pedido_key UNIQUE (id_pedido);


--
-- Name: facturas facturas_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.facturas
    ADD CONSTRAINT facturas_pkey PRIMARY KEY (id_factura);


--
-- Name: lineas_pedido lineas_pedido_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lineas_pedido
    ADD CONSTRAINT lineas_pedido_pkey PRIMARY KEY (id_linea);


--
-- Name: montaje_componentes montaje_componentes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.montaje_componentes
    ADD CONSTRAINT montaje_componentes_pkey PRIMARY KEY (id_montaje_componente);


--
-- Name: fabricacion montajes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fabricacion
    ADD CONSTRAINT montajes_pkey PRIMARY KEY (id_montaje);


--
-- Name: montajes montajes_pkey1; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.montajes
    ADD CONSTRAINT montajes_pkey1 PRIMARY KEY (id_montaje);


--
-- Name: oportunidades oportunidades_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.oportunidades
    ADD CONSTRAINT oportunidades_pkey PRIMARY KEY (id_oportunidad);


--
-- Name: pedidos pedidos_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pedidos
    ADD CONSTRAINT pedidos_pkey PRIMARY KEY (id_pedido);


--
-- Name: productos productos_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.productos
    ADD CONSTRAINT productos_pkey PRIMARY KEY (id_producto);


--
-- Name: proveedores proveedores_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.proveedores
    ADD CONSTRAINT proveedores_pkey PRIMARY KEY (id_proveedor);


--
-- Name: roles roles_nombre_rol_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT roles_nombre_rol_key UNIQUE (nombre_rol);


--
-- Name: roles roles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT roles_pkey PRIMARY KEY (id_rol);


--
-- Name: stock stock_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stock
    ADD CONSTRAINT stock_pkey PRIMARY KEY (id_stock);


--
-- Name: usuarios usuarios_nombre_usuario_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuarios
    ADD CONSTRAINT usuarios_nombre_usuario_key UNIQUE (nombre_usuario);


--
-- Name: usuarios usuarios_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuarios
    ADD CONSTRAINT usuarios_pkey PRIMARY KEY (id_usuario);


--
-- Name: clientes clientes_id_usuario_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.clientes
    ADD CONSTRAINT clientes_id_usuario_fkey FOREIGN KEY (id_usuario) REFERENCES public.usuarios(id_usuario);


--
-- Name: componentes_fabricacion componentes_fabricacion_id_producto_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.componentes_fabricacion
    ADD CONSTRAINT componentes_fabricacion_id_producto_fkey FOREIGN KEY (id_producto) REFERENCES public.productos(id_producto) ON DELETE CASCADE;


--
-- Name: componentes_fabricacion_antigua componentes_fabricacion_id_proveedor_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.componentes_fabricacion_antigua
    ADD CONSTRAINT componentes_fabricacion_id_proveedor_fkey FOREIGN KEY (id_proveedor) REFERENCES public.proveedores(id_proveedor);


--
-- Name: componentes_fabricacion componentes_fabricacion_id_proveedor_fkey1; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.componentes_fabricacion
    ADD CONSTRAINT componentes_fabricacion_id_proveedor_fkey1 FOREIGN KEY (id_proveedor) REFERENCES public.proveedores(id_proveedor);


--
-- Name: compras_componentes compras_componentes_id_componente_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.compras_componentes
    ADD CONSTRAINT compras_componentes_id_componente_fkey FOREIGN KEY (id_componente) REFERENCES public.componentes_fabricacion_antigua(id_componente);


--
-- Name: compras_componentes compras_componentes_id_proveedor_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.compras_componentes
    ADD CONSTRAINT compras_componentes_id_proveedor_fkey FOREIGN KEY (id_proveedor) REFERENCES public.proveedores(id_proveedor);


--
-- Name: compras compras_id_componente_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.compras
    ADD CONSTRAINT compras_id_componente_fkey FOREIGN KEY (id_componente) REFERENCES public.componentes_fabricacion(id_componente);


--
-- Name: compras compras_id_producto_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.compras
    ADD CONSTRAINT compras_id_producto_fkey FOREIGN KEY (id_producto) REFERENCES public.productos(id_producto);


--
-- Name: compras compras_id_proveedor_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.compras
    ADD CONSTRAINT compras_id_proveedor_fkey FOREIGN KEY (id_proveedor) REFERENCES public.proveedores(id_proveedor);


--
-- Name: facturas facturas_id_pedido_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.facturas
    ADD CONSTRAINT facturas_id_pedido_fkey FOREIGN KEY (id_pedido) REFERENCES public.pedidos(id_pedido);


--
-- Name: lineas_pedido lineas_pedido_id_pedido_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lineas_pedido
    ADD CONSTRAINT lineas_pedido_id_pedido_fkey FOREIGN KEY (id_pedido) REFERENCES public.pedidos(id_pedido);


--
-- Name: lineas_pedido lineas_pedido_id_producto_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lineas_pedido
    ADD CONSTRAINT lineas_pedido_id_producto_fkey FOREIGN KEY (id_producto) REFERENCES public.productos(id_producto);


--
-- Name: montaje_componentes montaje_componentes_id_componente_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.montaje_componentes
    ADD CONSTRAINT montaje_componentes_id_componente_fkey FOREIGN KEY (id_componente) REFERENCES public.componentes_fabricacion_antigua(id_componente);


--
-- Name: montaje_componentes montaje_componentes_id_montaje_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.montaje_componentes
    ADD CONSTRAINT montaje_componentes_id_montaje_fkey FOREIGN KEY (id_montaje) REFERENCES public.montajes(id_montaje);


--
-- Name: fabricacion montajes_id_pedido_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fabricacion
    ADD CONSTRAINT montajes_id_pedido_fkey FOREIGN KEY (id_pedido) REFERENCES public.pedidos(id_pedido);


--
-- Name: montajes montajes_id_pedido_fkey1; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.montajes
    ADD CONSTRAINT montajes_id_pedido_fkey1 FOREIGN KEY (id_pedido) REFERENCES public.pedidos(id_pedido);


--
-- Name: fabricacion montajes_id_producto_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fabricacion
    ADD CONSTRAINT montajes_id_producto_fkey FOREIGN KEY (id_producto) REFERENCES public.productos(id_producto);


--
-- Name: montajes montajes_id_producto_fkey1; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.montajes
    ADD CONSTRAINT montajes_id_producto_fkey1 FOREIGN KEY (id_producto) REFERENCES public.productos(id_producto);


--
-- Name: fabricacion montajes_id_usuario_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.fabricacion
    ADD CONSTRAINT montajes_id_usuario_fkey FOREIGN KEY (id_usuario) REFERENCES public.usuarios(id_usuario);


--
-- Name: montajes montajes_id_usuario_fkey1; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.montajes
    ADD CONSTRAINT montajes_id_usuario_fkey1 FOREIGN KEY (id_usuario) REFERENCES public.usuarios(id_usuario);


--
-- Name: oportunidades oportunidades_id_cliente_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.oportunidades
    ADD CONSTRAINT oportunidades_id_cliente_fkey FOREIGN KEY (id_cliente) REFERENCES public.clientes(id_cliente);


--
-- Name: oportunidades oportunidades_id_producto_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.oportunidades
    ADD CONSTRAINT oportunidades_id_producto_fkey FOREIGN KEY (id_producto) REFERENCES public.productos(id_producto);


--
-- Name: oportunidades oportunidades_id_usuario_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.oportunidades
    ADD CONSTRAINT oportunidades_id_usuario_fkey FOREIGN KEY (id_usuario) REFERENCES public.usuarios(id_usuario);


--
-- Name: pedidos pedidos_id_cliente_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pedidos
    ADD CONSTRAINT pedidos_id_cliente_fkey FOREIGN KEY (id_cliente) REFERENCES public.clientes(id_cliente);


--
-- Name: productos productos_id_proveedor_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.productos
    ADD CONSTRAINT productos_id_proveedor_fkey FOREIGN KEY (id_proveedor) REFERENCES public.proveedores(id_proveedor);


--
-- Name: stock stock_id_producto_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.stock
    ADD CONSTRAINT stock_id_producto_fkey FOREIGN KEY (id_producto) REFERENCES public.productos(id_producto);


--
-- Name: usuarios usuarios_id_rol_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuarios
    ADD CONSTRAINT usuarios_id_rol_fkey FOREIGN KEY (id_rol) REFERENCES public.roles(id_rol);


--
-- PostgreSQL database dump complete
--

\unrestrict QOoXCoPfD2GjakBiSrNnFSmLD4avssvnRHS5JwofK5uyb0pqhGGRmP1vh4pMUpt

