package com.rndeveloper.paparcar.fakes.data

/**
 * Las calles del mundo de demostracion, con trayectos que van POR LA CALZADA.
 * [MOCK-A-DEMO-ROUTE-MUST-FOLLOW-THE-STREETS-001]
 *
 * GENERADO por `mockkit/tools/fetch_demo_routes.py` — no editar a mano. Las polilineas son
 * respuestas de OSRM sobre el grafo real de Madrid, en el mismo formato precision-5 que lee
 * `PolylineCodec`, asi que entran literales.
 *
 * La RUTA manda sobre el pin, no al reves. Antes el catalogo inventaba una coordenada y
 * dibujaba hacia ella una curva recta que cruzaba manzanas enteras — y encima la firmaba con
 * `routeSnapped = true`, que es justo la afirmacion que la linea desmentia en pantalla. Ahora
 * el coche acaba donde de verdad acaba el trayecto: `endLat`/`endLon` SON el ultimo punto de
 * la polilinea, y la calle es la de esa calzada. No quedan dos fuentes que se puedan
 * desincronizar, que era el motivo por el que esto se generaba en el dispositivo.
 */
internal class DemoStreet(
    val name: String,
    val endLat: Double,
    val endLon: Double,
    /** Llegadas a la misma calle desde rumbos distintos: la calle de siempre, no el mismo viaje. */
    val approaches: List<DemoDrive>,
)

/** Un trayecto real: su polilinea y los metros que mide OSRM sobre la calzada. */
internal class DemoDrive(val polyline: String, val meters: Float)

internal object DemoStreets {
    val leon: List<DemoStreet> = listOf(
        DemoStreet(
            name = "Calle de Fuencarral",
            endLat = 40.423625,
            endLon = -3.700657,
            approaches = listOf(
                DemoDrive("c~yuFfjnUDEb@e@HKJKR?VBP?j@BR@B?`GR|FXF?^x@xEbKBFN\\BJ?H@NAp@?HAR@J?DBLBJBF`ADH@T@V@|@DnAFT@hBHX@d@D^@f@B\\@fDLL?d@BdCHf@BA^?DIvEAzAJhK?^EhAIb@ABCBADAD?D?F?H@FBDBDDDD@D@D?DAFABEDETBHBNBXLLF~BjAlEvB^PXRhBhBPPj@j@xFlGBDJLJJb@f@DB?NBD@D@F?HCNENCHCJ?PFN@BNPDBH@F?B?JC`@@FADB`DtBDBDBFDRJHDv@b@@@BHDDBB?D?B@LCB[h@IHADCDC@ABJZpApD|AlEVx@BFt@tB@D@FAFz@IRAd@CRAp@AD?H?T?`@?@?@???", 3154f),
                DemoDrive("gbsuFzgpUiA_AGEGCYWGIS[I[K_@CSEGEKGIEEIEQEQ@G@GBKDA@]Va@VQJMFMH_AZ_@F_AR_C\\mC`@}BXq@Hu@HG@a@Fi@HyB\\uC`@SDG@]D]FO@G@UD[DKBk@JO@g@JG@E?SEUEyCc@cHy@_@IGAC?GCICC?KGQIIEi@UIEq@KQC?N?P?N@ZF`@Px@DVHZBL@L@LBJVzBTlB@N@H?@B\\D^Fl@Bj@?J@\\?VC^CXAL?DALCLE?K@{ANyAPI@?T?FCHE@{EIE?GAwCGIAQtAEL[vAWhAEJEFg@jBAFeBgAm@_@QKOIMICF]vBEVEXA^AJ?H?J?b@@~@?R@^?HT?`@?@?@???", 2668f),
                DemoDrive("gjwuFfwxUlLaJn@g@`@g@`A}Ax@_BvAmCbAmB|AuCtH}N~@}BXeAf@mBdBwGhBkGz@yB|@yAvB}AvAo@~AMx@KjAFzA^zAd@p@Fr@@j@JbARn@VlBp@jBbA|@`@h@P`AT|@@\\@`@A?i@?K@E?U?EDgJ@I?{@@w@?Q@u@m@Ku@G[C}@G_CS{E]oCSEAyCe@_Gg@C?eACK?KEKMIMEKEQCSCSAwAEuGMcHAsACq@ES?A_@y@_CqDa@q@CGs@qAEI[m@QYq@q@WIOIQMGMEKCGAECEEQGWCICGQa@a@{@}@iBMWGKCCIEIAK@KDMLWTgBpAKHKFEB]TUNeBjAWNMHoBnAYRYR]TSJ_Al@IDOJe@XgAt@ECIEEEi@]mCeB{DeCECKGqBqAEEMMPsDDw@@A?I??Ba@XqF?K@GDy@DmA@OBi@HeBPcED}@?C@I@g@HeABc@Be@BW?IRkBd@sEHoAPoBBO@IFa@NGVKDAXOJCDADAjA]LEdCu@XIt@St@Wv@UDADAXKPGDALEbA]j@ONG`AUDARGz@IRAd@CRAp@AD?H?T?`@?@?@???", 6432f),
            ),
        ),
        DemoStreet(
            name = "Calle de Hortaleza",
            endLat = 40.422945,
            endLon = -3.699153,
            approaches = listOf(
                DemoDrive("mzyuFjanU[c@hEsEKa@zAeALM\\t@BBL^IFEFCFCHCH?H?H?D?FBJ@B@B@DDFBBDDB@DBD@B?H?DADBDBDFBDFJRf@z@dBjAhCHPN\\FNP\\FJ^x@xEbKBFN\\BJ?H@NAp@?HAR@J?DBLBJBF`ADH@T@V@|@DnAFT@hBHX@d@D^@f@B\\@fDLL?d@BdCHf@BT@^@p@@|BFV@nBD`BFf@Fb@FnCZZDZBtD^bAJ^DrFl@\\DzAPzAPd@DMj@C^Gl@Aj@GjBEfBAH@XBl@?PBj@FHHJHHHDJDLBTBLAFAJCLEr@?L?J@JBD@D@bA^lBp@j@RVHVJDBh@Rt@VPH`@LB@fA^Ah@?F?f@?P?N?DAbEAlB?N?J?LAtB?f@?fB?BAD?FAD?HEvBAZCTAPAHIdB?PQtAEL[vAWhAEJEFg@jBAFm@_@", 3648f),
                DemoDrive("m{ruFbcpUqBzBGHIFEDA@A@KHIDG@E@IAOEwAkAGEGCYWGIS[I[K_@CSEGEKGIEEIEQEQ@G@GBKDA@]Va@VQJMFMH_AZ_@F_AR_C\\mC`@}BXq@Hu@HG@a@Fi@HyB\\uC`@SDG@]D]FO@G@UD[DKBk@JO@g@JG@E?SEUEyCc@cHy@_@IGAC?GCICC?KGQIIEi@UIEq@KQC?N?P?N@ZF`@Px@DVHZBL@L@LBJVzBTlB@N@H?@B\\D^Fl@Bj@?J@\\?VC^CXAL?DALCLE?K@{ANyAPI@?T?FCHE@{EIE?GAwCGIAQtAEL[vAWhAEJEFg@jBAFm@_@", 2510f),
                DemoDrive("_dwuFbrxUdF}Dn@g@`@g@`A}Ax@_BvAmCbAmB|AuCtH}N~@}BXeAf@mBdBwGhBkGz@yB|@yAvB}AvAo@~AMx@KjAFzA^zAd@p@Fr@@j@JbARn@VlBp@jBbA|@`@h@P`AT|@@\\@`@A?i@?K@E?U?EDgJ@I?{@@w@?Q@u@m@Ku@G[C}@G_CS{E]oCSEAyCe@_Gg@C?eACK?KEKMIMEKEQCSCSAwAEuGMcHAsACq@ES?A_@y@_CqDa@q@CGs@qAEI[m@QYq@q@WIOIQMGMEKCGAECEEQGWCICGQa@a@{@}@iBMWGKCCIEIAK@KDMLWTgBpAKHKFEB]TUNeBjAWNMHoBnAYRYR]TSJ_Al@IDOJe@XgAt@ECIEEEi@]mCeB{DeCECKGqBqAEEMMPsDDw@@A?I??Ba@XqF?K@GDy@DmA@OBi@HeBPcED}@?C@I@g@HeABc@Be@BW?IRkBd@sEHoAPoBBO@IFa@NGVKDAXOJCDADAjA]LEdCu@XIt@St@Wv@UDADAXKPGDALEbA]j@ONG`AUDARGz@IRAd@CRAp@AD?H?T?`@?@?@???@A@A?A@Cp@oEVqB?C?Em@_@BEl@qBfC|AHDEFg@jBAFm@_@", 6720f),
            ),
        ),
        DemoStreet(
            name = "Calle de Atocha",
            endLat = 40.409210,
            endLon = -3.692060,
            approaches = listOf(
                DemoDrive("afwuFftlUBmB?SP@pFRB?P@J@zADtBJ`@@B?F@D?`@@^BVv@Xv@Xx@L\\?Bd@vAPf@Tj@Rj@JXXt@BFL\\FTRn@Nb@Vv@Nd@Ph@l@fBBDFRNb@Tl@p@nB`@lA`@hATp@?@X|@d@pANd@FNFTPb@t@zBVn@DJPf@DLHNdA`DNd@DRDTZbCZxBVxB^xCBRBVXnB@TBN?F?VATENIPIXCLAR@L@NDPFNJNJHFBLBJ@XGJCLMHMFMBQVET?F?hADb@@`CJZB`CF`BF??v@Bn@?rAAbACNAz@AD?^AV?\\A`CEV?lACVAfBAH?VA\\?DAF?t@CnC?`IKF?F?T@B@D?B@B@D?@?BARCDALFNLrFxEDHJVe@|@OZWf@A@Yj@OXOZCFQ`@KVCDA@]VWf@CF", 3455f),
                DemoDrive("ggpuFppnUDBNNVRZPRJdFfB~@j@bA`Ab@h@lC~C`AnABDf@n@hAtA`BnB|@fA|BrCx@dAPRHTFR@N@XBNBHB@FDDY@S?OE[AEKa@Q[w@}@W[iDaE[W}D_FEEKMiBuBk@q@yAcBcAcAeAm@gFgBa@UUOWYUa@oBqECEAAIIKAQa@AAKSQa@yAiDISEIAEWo@Oi@@QEUa@cBACMKKYCICGCCAAACC?CAA?C?A@E@OPC@GHKLu@x@UTGJIJABONs@x@o@v@aBlBSVwA`BOPWXSTmAtA_BjBMNUXm@p@UVGFYZa@f@SVED_@b@UVSTyBfCWVuChDi@l@mBzBYZiBrBo@r@{BdCOPQRiDvD[^A@OPGHwA~AEDuA~AONUVUXqC|CGHIFEDA@A@KHIDG@E@IAOEwAkAGEGCYWGIS[I[K_@CSEGEKGIEEIEQEQ@G@GBKDA@]VWf@CF", 4254f),
                DemoDrive("{ktuFpjwUf@y@JQb@q@PYTSGQMg@OgCCS?EAQOqCAYAKEg@Eo@E}@GiAAi@?}BAO?i@?K@E?U?EDgJ@I?{@@w@?Q@u@@u@@m@?q@?aA?S@_@?k@@_CHq@Pi@Fa@@gCCa@BYDIRSdA^LD^NzIlDXLPHF@HBXUPKDEHKNKbAi@jHeEZ[Tg@|@yCNi@Pi@j@gBj@uBtA{Er@uC^gBj@mC@WDYVkClAuILkAD_@@GDg@Fk@Fc@BUFg@h@mFBUl@}FRiBJaAPeAHIHKDIDOFUBQ@O?SCSPMJIHGx@m@dAw@GWGYWmAG]g@aCEQESkA_GESa@kEM{AEYNIn@_@nAo@^UDG@E@K@Oe@a@CCCCcA}@EGAIAIAG?I@GV}J@I?EAEAEsAI??UA_EWE?_AEQA]CSAEAE?e@EIAGCECGEi@[wAkAGEGCYWGIS[I[K_@CSEGEKGIEEIEQEQ@G@GBKDA@]VWf@CF", 4486f),
            ),
        ),
        DemoStreet(
            name = "Calle Mayor",
            endLat = 40.416053,
            endLon = -3.708320,
            approaches = listOf(
                DemoDrive("ynxuF~yoU@lA?\\@b@@dCBbB?B@dB?XHtH?Z@z@B~C@xA?VDpE@pA@j@?F?FD`AJt@@DBN?DRjAJr@@BLz@DVBP@F@DVdBZpB@DHh@DV@F@Df@bDDZHh@b@rC@FCH?LCNEVAB?DAD?F?F@F@FBDBDBBDBD@D@D?DABABA`ALJGDBdDz@B@DHRPHF^TTBd@B^GF@Ct@KpDEbBA^?P?R@N?NAP?H?HAb@AVIzD?H?PAR?HM~F?@AT?T?FGnEAR?LGfECdB?TL@dBF~@BfADD@R??Z@LLVFLBBBBz@h@jAt@JRv@d@h@ZDBBBHHJZ`@_@tA}@l@e@DAROVOLItA}@TOPKh@_@BC`@U~@k@F?F?FBHDlB~@NHjB~@NF~BhADB`Ab@\\TJFRJJBJ@HAFAXWx@}@TSNQ|AiBV]HKNQLKDEDCBCPQDE@ADCFELITGRGTCPAZAbADdBBhY~AhDTF@T??O@KAG?GKoAKuAKs@CYUiCMmAMmAE]Gg@Gc@[iBAGAEACAMGMK{@?EAEAYCSCOESCQGa@", 4667f),
                DemoDrive("asquFbxqUb@[DEDAtBjBNLRNtAnA@FAFA@EBeEpC_DxBmAx@EH@JjArBiBnA_@VIDSXs@`@EBQLWLwBrAUNMFIFcAn@[PcAp@mAt@OHODSDgBb@SHg@RKx@a@zDCXsAL_AHSFUPk@lEStAi@bFADK\\I?G?G@GBEDEDEFCFAHAFAH?H@HMROLGFaBhAy@j@aBlAc@\\ULUN[Lm@VWJs@XmAd@m@PI@I?WESEGCE@G?EBa@L_@LE?yCQSAa@Ci@CqBMO?SCm@CwFYi@GYCG?E?[C@KAG?GKoAKuAKs@CYUiCMmAMmAE]Gg@Gc@[iBAGAEACAMGMK{@?EAEAYCSCOESCQGa@", 3044f),
                DemoDrive("sjuuFhgzUHPP`@Zn@NR\\x@Nh@Pp@Nn@Jh@Nn@JZRVLJHXHRLLb@`@j@j@`@RDFLJBDBFRb@JR^j@PEnBs@TGJAREz@K\\FCLEVANGfA?ZAT@VBTBPBXDd@Dv@D\\b@lBH^JVLRTNr@ZPHJDVHP@H?DABADC@CBQDOHMNMd@]FKDIDQBEO}@]eBa@yDQaAOcAWeAMm@EQRE^AZGx@c@ZKbD_@`@KNSHOHSDKHSHSFQTm@DGDGBEFEFEJCREpD]dAKvCg@LCLENINILIHCFCD?b@HJ?HAXO`Ac@VMDCf@YJETOVQXv@^lAdAnDPp@V`AxA|Fh@rBd@fBd@|AXbArBvHZnAFNDNFRHTDLNECMSi@IYCIyBcIo@yBo@yBw@sCs@qCqAkFo@yBmA{Da@mAe@cAM]BYAQG]_@}AEQe@gBSu@Qo@AIGSuAgFSw@YaAW{@AG[gAOm@YgAEKEUGQQi@_@sAESUy@Oe@GUGSGSm@_Co@_CMe@yAqFMi@yAqFOk@GUwAoFQs@}A{FM_@Ga@Iu@ACGg@GQMg@OgCCS?EAQOqCAYAKEg@Eo@E}@GiAAi@?}BAO?i@?K@E?U?EDgJ@I?{@@w@?Q@u@@u@@m@?q@?aA?S@_@?k@@_CBkI@m@?o@?iB@aD?_H?m@?]?g@?U?oC?cBE?E?[AA?C@A@ABWtAGNCFEDEBG?CAEQIKECECaALEACECe@_@FE?KCCYUiCMmAMmAE]Gg@Gc@[iBAGAEACAMGMK{@?EAEAYCSCOESCQGa@", 6985f),
            ),
        ),
        DemoStreet(
            name = "Calle de Toledo",
            endLat = 40.402849,
            endLon = -3.713321,
            approaches = listOf(
                DemoDrive("c|uuFhypU??EvBAZCTAPAHIdB?PQtAlDbALcC?I@KHwBBS?QB_D?MJ?hDA\\AN?`DMJ?LAD?RGCUACAMSiBKeAEYAWAO?A?E?O?K?E@K@C?KR?F@RDD@H@|E~@r@LvCj@D@z@NvB\\D?B@RAXGd@QZYhASXKDAjC}@t@UlBk@FA^KPGh@MZM`@IlDw@jBc@NEzD}@TGNE~@QJAVERCL@LDJJLDJBfBnADDFDXTFFZ`@N^P^DHBJFNFVLn@jArG`@vAF^H\\BTnAzGH`@l@bD^nBBTJh@XzAh@zCFZ^zBJp@RfA@BBJFZXbBHZH`@Np@ZbBJn@Nv@XnAFXLr@Jl@r@bEDJ@JDNJl@l@dDH`@DXRdAf@pC^nBPbABPNv@Tn@Rd@LTR^T`@HPBFfC`FTf@Vf@^z@p@rAF^BX@LCD_@BYC_A]gBq@yCeA", 4362f),
                DemoDrive("k`ouF~xrUk@FOBM@QHOJMPEHENYbBCTOEu@WIAQEcAc@YM_@QMGuAk@AAQGw@[[KERADm@vCMh@ADCNGVAFw@jDq@bDGVCFABEDCBGBK]Ss@g@iAg@mA]u@Q]M[m@s@sAkAy@k@g@[]UOI[Qi@a@CE{DiDgFoEGIOY_@a@O_@CECY@YFc@Na@NWPS^U\\Id@AV?VZFb@@\\Ef@ANUj@uAhEm@tCQ~AOr@u@nD_@fBCJSv@Uv@aAxDq@tBOd@iBhCg@t@c@`@k@\\u@LkBDo@F_A^e@v@[l@_@r@e@lAK\\Kh@AZ?d@?`AG^CRGVGXKIOCO@IBGDCD_@BYC_A]gBq@yCeA", 3129f),
                DemoDrive("gdsuFpo{UFmB@UB_@B?JAdAOHAf@IPAD?J?x@e@pBkAXbArBvHZnAFNDNFRHTDLNECMSi@IYCIyBcIo@yBo@yBw@sCs@qCqAkFo@yBmA{Da@mAe@cAM]BYAQG]_@}AEQe@gBSu@Qo@AIGSuAgFSw@YaAW{@AG[gAOm@YgAEKEUGQQi@_@sAESUy@Oe@GUGSGSm@_Co@_CMe@yAqFMi@yAqFOk@GUwAoFQs@}A{FM_@Ga@Iu@ACGg@GQMg@OgCCS?EAQOqCAYAKEg@Eo@E}@GiAAi@?}BAO?i@?K@E?U?EDgJ@I?{@@w@?Q@u@@u@@m@?q@?aA?S@_@?k@@_CHq@Pi@Fa@@gCCa@BYDIRSdA^LD^NzIlDXLPHF@HBH@J?NCLCVMDC`Ba@jCo@j@OtDaATEj@OdCq@XGbAWlFsAbA[pAYb@FBDDDDBD@D?D?DCDABEBE@E@E@G?E\\YDARGHAr@S^I\\KPEzAa@XGpA]|@ULCd@MTG@?x@UPELAN?d@QHANCLGHEHGJUBIJk@E[KOKIOCO@IBGDCD_@BYC_A]gBq@yCeA", 5559f),
            ),
        ),
        DemoStreet(
            name = "Calle de Serrano",
            endLat = 40.453839,
            endLon = -3.679054,
            approaches = listOf(
                DemoDrive("i{_vFjcjUfA`B[b@W^ILMPY^w@`Ao@gAKOEF_@h@ORLNDLFJBPDh@@VXPLIZULKPMx@s@ROZc@b@GTFPFTXJ^Ln@b@|ED`@BRfAzL?F@F@NFj@@Nh@KLCB?REh@Kn@MlDq@XEDAREzAYD?PCPEdEw@t@Kj@KPCD?XED\\@FJ|@f@tF^dEZdDLbBJpALtA@X@XA\\It@G\\IXU|@]jAq@fCo@~BITIFGVx@f@NJtEjCLF\\Pf@LTBb@FF@lEh@F@RDPB|@J|En@ZDt@HTDhALfCZZDPBXDZBP@^DZ@lCNh@B@@f@BX@l@D\\@b@JH@bDRF@^BZ@L@`FXP@PBZBTFB@PBTJd@b@p@j@LTXf@Zj@f@z@DHDH^n@HLFJ", 3736f),
                DemoDrive("o_yuFfalUVjA@FBFs@rA_@t@i@p@KJKHEDA@g@ZOJEBEAI@GBC?KFE@KHGBA@ABQNCHC@}@j@cAj@UNULgC~AuAx@oBfAsA|@k@^WNUPC@SLSYKQw@kAOSAAuAkBQUQWm@y@MTy@tAaA|AKF_CzBaAx@IHMDyCx@MBkEdAuCn@G@C@C@CBA@ABCDGVGRADEPADCDCFAD?D?D?F@THf@DN@X?F@tA@NQ?c@C}DOEAK?uAGC?W@G?oCJi@B]@C?iABs@Be@@O?Q@{BHK@E?QHGHGHGHAD?DKOCG", 2234f),
                DemoDrive("mb}uFvytUR_ADQFU@GBIRaAFY^eBBOFUDQJe@p@mD@E@GFc@Rq@f@oCJi@@GFi@B[BQASEOEMGIIAG?EBGBM@E?I?KAMEOKaCyAqCcBaCuAQMuBmA{A}@_@Q]QWOOKMI]WYQYQEEEAYYOI[MuB}AIKGGCC[g@KSIQKQSe@CGEKGIIIIGGEGCQIEAIAICG?OAJe@N{@H_@Ji@DUHe@VkAH_@BK\\wADIVk@BG\\w@?AFOHQVk@LYLYVk@j@qA\\w@N[FOHSHS~@uBXo@HQJSx@iBn@yAFMVm@j@uAHUFQ@EBG@IDSD?D?BCBEBI@GNo@Li@Jc@BM@EDWj@gC?E@O?CFW\\{Ax@mD@GPo@Ja@BODQDQBU@g@@WRcGDkB@a@@_@DwD@C?I@OD[LCLCJIJKJKFODQDU@O@OASEWGSGOIKKKKGMEAUAI?E@e@FyC@c@HaDDaB@c@F_D@m@@o@Be@FcCLwDH{BTBnC^`@DnDb@lCb@ZFtATpEr@JBFDF?D?@AB??A@C@A@C?CAG]c@B]T}J@SHkE@{@FqDF[T[FJ", 4471f),
            ),
        ),
        DemoStreet(
            name = "Calle de Alcalá",
            endLat = 40.438009,
            endLon = -3.637593,
            approaches = listOf(
                DemoDrive("iw|uFl~aUFHPLPJ\\`@Zb@h@bA^dApCbKBHLb@HVtAs@DCFC@?HCJ?LBFBHDFHFJH\\dAbE|@dDLd@@FLb@FTFNR^LTT^Z\\\\\\\\Tj@Xl@Nt@Hb@?`@Eb@KFC^KvBOJABArBOxDWjE[d@D@FBFBFDDDBDDD@DBF?D@DAF?DCDADEBCFGBIDI@G@GVGDAhIw@PCXEPGfE{BjAo@t@a@dCsA|A}@dAi@FDL@PILEHd@F^bAzFLn@F^z@tEt@jEFXBHF^r@~DTnABJJn@N`AHb@DVJl@X|ALp@@DBTLr@F\\Lr@DRBJ\\pBP~@l@~CPbAn@rDJj@BJVvAJN`@`CLv@Nr@F`@F\\", 3355f),
                DemoDrive("}yuuFxxcU~@m@LKHGVLxBbBFDNLVNPLH^H^BRBJBFDF^ZVPMTYr@}@nBw@hBYn@A@IPGNq@|AsAxCMVYp@]t@EHQ`@{AjDKTQVUVKJKDUFG@K@[?IAEACCEA?OAY@k@@}FAu@Es@MwAGg@WaDQsBIo@OcA_AoFUuAEUIs@AEIg@DOBOBQASAQEQGQgBhAa@V[RaAn@KFiCbBYRk@^eEnCo@b@_@TOJEBUNoBpAgBjA_@V_@Tm@^w@h@EB[RsD~BQLUNqEvCe@Ze@Zi@\\YRiEpCw@h@[Ru@f@]TKHa@VkCbB]T]TUNiBjASLkAr@g@ZGDe@Xc@XWPGYAGG]", 3344f),
                DemoDrive("g_zuF`wlULSFK|@sALNzB`DnBgAtAy@fC_BTMTObAk@|@k@BAD@PMTOFAAQAMAEAKU_CIi@U{AUaAMe@M[Q_@i@gBc@mA[o@Wk@g@}@eAuB}@gBy@gBs@yAwAoCqB_E]y@s@kB_@eAQo@_@sAQs@Ik@oBeIc@mBc@eB[iAC]EQCMMq@Go@Ai@Ae@AYEYESUu@]e@Wi@_@cBiAkE]mBKc@kBoGu@yBq@_CA[F_@rAwAD[EYUw@mCqIGS\\[X[dAqAb@w@NU~@sANQHQBE|@cBt@_BdAcBHQLUPc@Pe@HSH]R{@Py@HUdBuEHUN_@t@uBbAcBpAoCd@s@`@c@r@i@bAm@TQPKRMNIPK\\UFEROXQ|KkHhBoAh@[\\URMHGTMPIGQCMCKAKGYAGG]", 4371f),
            ),
        ),
        DemoStreet(
            name = "Calle de Génova",
            endLat = 40.426002,
            endLon = -3.692330,
            approaches = listOf(
                DemoDrive("imzuFhvlUp@tAfAtADF@@HJ|AtBtAlBcAzAa@l@KHAJAh@C~@CXCRCJbGZvBLpAHH?TBP?B@h@BVBRf@h@pAh@lA\\t@BBL^IFEFCFCHCH?H?H?D?FBJ@B@B@DDFBBDDB@DBD@B?H?DADBDBDFBDFJRf@z@dBjAhCHPN\\FNP\\FJ^x@xEbKBFN\\BJ?H@NAp@?HAR@J?DBLBJBF`ADH@T@V@|@DnAFT@hBHX@d@D^@f@B\\@fDLL?d@BdCHf@BT@^@p@@|BFV@nBD`BFf@Fb@FnCZZDZBtD^bAJ^DrFl@\\DzAPzAPd@DMj@C^Gl@Aj@GjBEfBAH@XBl@?PBj@Fx@B^@P?R?FAFAPCLCJENO\\k@vAWp@", 3306f),
                DemoDrive("{isuFn|nU??HIHAJ@n@RrBr@HBLK`@oBLGFGFWGiAGm@AOc@uEAQs@LYFAMi@sFAStBa@fB[BA^GBZ@F@HDf@X|CD\\Dd@JrABLPlBDVB`@BVBVBPBRB`@@J@R?H?bAA`@MrAOlAGn@St@U|@Ur@EPENM`@Uv@Q^KRGLUZMPCDm@dAw@`Be@|@OZWf@A@Yj@OXOZCFQ`@KVCDA@]Va@VQJMFMH_AZ_@F_AR_C\\mC`@}BXq@Hu@HG@a@Fi@HyB\\uC`@SDG@]D]FO@G@UD[DKBk@JO@g@JG@E?SEUEyCc@cHy@_@IGAC?GCICC?KGQIIEi@UIEq@KQC[GY?ICcFgBgFiBEA[MKEiBo@ECa@MaA]iBq@_A]EAKEMEIE_@OSIAGAICICCGEKEUIy@USEC@C?CDC@CDAFCJ?PBj@Fx@B^@P?R?FAFAPCLCJENO\\k@vAWp@", 3729f),
                DemoDrive("gqwuFluwUFPLZWTqAx@{@b@c@To@d@IFQPcC`CYVYVkA|@WRA@IFc@\\cA|@_@ZWTGFAICGA[Em@@[@a@Bg@FwAHiCBm@He@HSLORURSVWNUZc@JOHQ\\s@FYDY?WAUEUCYAOAO@O@IBMFSRa@Pe@DU@U?WA_@Ac@@[BYF]@U?[?e@B[F[BIJOJMJS|HwGxDkEVINED@B?D?B?BADCBABCBE@G@E@G?G?GAECGB]Fm@|AoFHm@Fa@AWGg@AEAi@CQWgAc@qBI]I]k@iCI]k@iCS{@_AaECMs@aDCICKAIAK?KAK?O?O@GFkAFeAL_CFgA?A?IBYBc@FqA?CT}F?E?GB_@PsDDw@@A?I??Ba@XqF?K@GDy@DmA@OBi@HeBPcED}@?C@I@g@HeABc@Be@BW?IRkBd@sEHoAPoBBO@IFa@BMDi@BYJ{@@EJ_A^iD^wC@EJ{@Jy@Fc@f@wDDc@@EZeC`@_D?GDc@BYDSBMBIDKd@oA?CBCd@kAFSnAwC@GJW^}@BKbAcCFMHS@Gx@sB\\w@Vo@b@{@DGDIJQLQJQb@i@FGBIBI@M@KAGAICICCGEKEUIy@USEC@C?CDC@CDAFCJ?PBj@Fx@B^@P?R?FAFAPCLCJENO\\k@vAWp@", 4982f),
            ),
        ),
        DemoStreet(
            name = "Calle de Sagasta",
            endLat = 40.428655,
            endLon = -3.700702,
            approaches = listOf(
                DemoDrive("g~zuF~inUg@bBCFCHd@TXNJDBBPHtCxAnAv@jA`A|@x@\\\\`@\\DRJTDDFDPDJALAFCFCLEPGPELABAp@BpAFtAFnBFh@BxAFb@BR@F?bCJP@bDLtDNn@Bb@BtCHT@r@DF?TBX?`ADH@T@V@|@DnAFT@hBHX@d@D^@f@B\\@fDLL?d@BdCHf@BA^?DIvEAzAJhK?^EhAIb@ABCBADAD?D?F?H@FBDBDDDD@D@D?DAFABEDETBHBNBXLLF~BjAlEvB^PXRhBhBPPj@j@xFlGBDJLJJb@f@DB?NBD@D@F?HCNENCHCJ?PFN@BNPDBH@F?B?JC`@@FADB`DtBDBDBFDRJHDv@b@@@BHDDBB?D?B@LCB[h@IHADCDC@ABC@C@C@CBABs@hAILk@|@Wb@CDCFAB?D?F@B?DBF@F@DZlAVfADPBJd@jB@D@DBFSF}Bp@GBC@KBaG`BE@EAC?IAVaC", 3845f),
                DemoDrive("}atuFpgpU[DG@a@Fi@HyB\\uC`@SDG@]D]FO@G@UD[DKBk@JO@g@JG@E?SEUEyCc@cHy@_@IGAC?GCICC?KGQIIEi@UUo@k@OKCQAWEGC_Bk@[KICIACA}Bw@MGA?CA_C{@g@QEA]MMGgBk@EAi@QAj@ARANEVAFADE\\CNEVCJQlAQlAAF?DAHCJCPAFAFg@lDEXALAFGPGTG^AFEXGJEJEFEHCHgApAONGHEDgAnAaAjAMJEHMRiAnBCBILCDIHEDCB[h@IHADCDC@ABC@C@C@CBABs@hAILk@|@Wb@CDCFAB?D?F@B?DBF@F@DZlAVfADPBJd@jB@D@DBFSF}Bp@GBC@KBaG`BE@EAC?IAVaC", 2520f),
                DemoDrive("gexuFdayU_@Hc@GMEIGIICMCOAK@S?UJgC@OHoE@a@@k@AwADY?O?]?MAMAICGA[Em@@[@a@Bg@FwAHiCBm@He@HSLORURSVWNUZc@JOHQ\\s@FYDY?WAUEUCYAOAO@O@IBMFSRa@Pe@DU@U?WA_@Ac@@[BYF]@U?[?e@B[F[BIJOJMJS|HwGxDkEVINED@B?D?B?BADCBABCBE@G@E@G?G?GAECGB]Fm@|AoFHm@Fa@AWGg@AEAi@CQWgAc@qBI]I]k@iCI]k@iCS{@_AaECMs@aDCICKAIAK?KAK?O?O@GFkAFeAL_CFgA?A?IBYBc@FqA?CT}F?E?GB_@PsDDw@@A?I??Ba@XqF?K@GDy@DmA@OBi@HeBPcED}@?C@I@g@HeABc@Be@BW?IRkBd@sEHoAPoBBO@IFa@BMDi@BYJ{@@EJ_AVaC", 3532f),
            ),
        ),
    )

    val transit: List<DemoStreet> = listOf(
        DemoStreet(
            name = "Paseo de la Castellana",
            endLat = 40.437515,
            endLon = -3.690259,
            approaches = listOf(
                DemoDrive("cu|uF~hlUYAG?EEO?Ab@?^Ad@Av@Ap@Ah@A\\?b@Aj@AZCzA?LA|@C`BCfACdBA\\?TZ@L@`FXP@PBZBTFB@PBTJL@J?NEfAO|AHt@Dl@BB@Z@l@D~BNR@r@DP@F@f@Bh@@d@Dp@DlAF`AFxBJvAHD@J?d@BL@D?ZB\\B^B\\R~CPrCPd@B\\@pEVH?|DT\\B\\BbGZvBLpAHH?TBP?B@h@BVBRf@h@pAh@lA\\t@BBL^IFEFCFCHCH?H?H?D?FBJ@B@B@DDFBBDDB@DBD@B?H?DADBDBDFBDFJRf@z@dBjAhCHPN\\FNP\\FJ^x@xEbKBFN\\BJ?H@NAp@?HAR@J?DBLBJBF`ADH@T@V@|@DnAFT@hBHX@NFD@BD@B@D?D?Fs@p@_Ax@EFEHEN?H?H@J@HDBBBJD^XNLb@XBBBDV^?h@?F@TBTGFEFORCB]^YRQLiGtDk@X", 3734f),
                DemoDrive("wyuuFvgnUEI[g@Uc@Q]Uk@t@zBVn@DJPf@DLABAD?HFPd@rARj@HVDPDN@H?DAFAFCDC@EBIBG@E?iAA}AIO?[ASA{BIwBIWAaAEqCMe@C_@Ac@AiDMUAqBIeCIEASAaGSWAyDOs@CIzEIjECbACbBAF?JA\\Kd@KrGD^Af@AZUCoMyBQ?oCVuCZc@@M?MAE?g@GM?OBI@GDGDGDGFEFORCB]^YRQLiGtDk@X", 2620f),
                DemoDrive("q}yuFt_wU??AGCCSBG?CD?D@l@UBI?GoAAQ?OBIdD_@pAMPCF?lAO|CYXCDANAL?H?DLDHFHFFHDJ@L?PGHIFKFO@QN]DIBEhBoCfBiC`@i@RY`@u@R_@P_@LYNe@j@oALi@JYt@cBr@aBL[Jc@Fc@A[Ca@I[KUMUSO}@w@][IIKKSWe@o@e@w@{DyGYg@So@Go@E{@Ay@^cUFaGHqFB_A@S@_A?W@m@FwDBiA?E?SFmF?Y@_@?G@o@?YDaC?ADeG?E?G@[?c@@E@{@FgD?GBe@LyHDuA@Q?G@c@?O@Y?G@g@BoA?E?QFoC@OBsA?W?G@a@JmG?E@YBsAJcF?G@YLyH?G@KFkE@y@@INs@BQBU@IFk@d@a@DEn@e@rCeBxCkB~@k@FEBADCDATGPCVGPGBM@E@G?I?GAECECEGGM?OBI@GDGDGDGFEFORCB]^YRQLiGtDk@X", 4498f),
            ),
        ),
        DemoStreet(
            name = "Calle de Bravo Murillo",
            endLat = 40.446395,
            endLon = -3.703958,
            approaches = listOf(
                DemoDrive("eo~uFn_oUAOQo@AYAU?CCa@rEi@RCjBSPCxAQTCpAE^Ad@CJ?ZAbA?p@BjAF~@D\\@hAFl@BR@lAFt@BhBJhADN@X@An@CpBCj@Ab@C~AGvC?JAd@IpD?HCN?@KTQDOHIFILGLGNCLCTAL?L@R@BBNDNDJFJ@BHHLJNFD\\?NAd@@JGrCA^A^A`@MlEIvCEh@V@^BxAHjCL\\@T@tCLrAFZBR@`@Bp@DxBLR@T@nEPfADV@r@B~@DnADL@J?P@J@F?N@R@fDJl@D^@pETj@Bz@Dr@B~BJx@BR@Ad@ANAh@IhE?PA\\G~AEdBCt@IP?PEzBGbDE`DCjAEjBA|ACpDCbB?^rAJN@d@Dj@DTLLFLH@HBFDDDFDBB@DBD?F@DAFAFCDEFEBGDK@KPEDCL?DAT@", 3887f),
                DemoDrive("cpwuFd_qUqAb@GBE@E@QHWJUJSHqA`@c@NIBy@XE@EBUHQAIDg@PGBEDw@VgA\\KDSFmBp@cBj@QFE@u@Tw@VE@iA^E@WJ_AX[LaA\\E@[JWFC@[JuAd@mDhAEBYHy@VEByAd@g@PQF]JaA\\YHUHg@NA@I@IDWJ{E|ASFYJi@PcDdA[JkDhASH_@LaEpA[LaCv@KDwAb@C@O@S?CGEEEEECECGAG?E?G@EBC@C@CBABCDCDCFAFAF?H?F@H@F@HBFDDDFDBB@DBD?F@DAFAFCDEFEBGDK@KPEDCL?DAT@", 2123f),
                DemoDrive("ut{uFttyU@AbBiDvCeGzDaIt@wAVe@Ze@`@g@`@i@vAuAfFgEnEuDfFqEr@k@r@m@|@u@zCiCvDeD|@eA~@uAR_@|@cBb@aARe@zCiHVi@Te@Vc@Vc@fBiClBoCtAsBp@gAZi@j@oALi@JYt@cBr@aBL[Jc@Fc@A[Ca@I[KUMUSO}@w@][}@wAm@eA_E{GEIAC_@e@o@aAOW[e@GI][CGOU[i@iB}CU]mCsES[?AMQOYKQKOgCgEQ]e@u@MSi@}@Yg@w@qAmBeDMQ?AIMAc@Sc@OWCEOYQ[iC{ECEQ[_@q@MSm@kAEEQc@GMOQOIc@CmAv@aA\\G@C@E?C?CCACCGFwBBiAHsC@i@F_DB_B@a@@u@DK@KPEDCL?DAT@", 4459f),
            ),
        ),
        DemoStreet(
            name = "Avenida de América",
            endLat = 40.437747,
            endLon = -3.677397,
            approaches = listOf(
                DemoDrive("qv|uFtxiUEZcBk@GAWKaA]mBq@EGAIf@y@NSNSFIDErAiAFGTQRQBAPOBCFGHGpAeAt@q@lAgARQZWPQ`@_@LKBCXWbByAnAeAt@o@HGHIDE~BqBNMtBkBPOJT@BBDvDlJJTL@HGXUVQtAiAdC}ALIzAaArA{@NINK|BeBNMPFRE^U`@S^SJALBJFLP`@rAX^hBlGzA`GlAzErAnFVhAt@~CjA|EBJJXjE~Pf@`Bj@xAJ^Zr@dAxBj@hAVtANl@P\\xAvCz@dBLVz@`B?BLZHNn@lA^r@FNHPHRRj@\\dAHJNf@\\tAb@rBX~AJhADh@TzBTpBRnAP`AD`@@FBA@?@?@?@@@?hA~BHNFLFJFLFJFLFLZl@JRh@dA@B?FABABCBC@C@A?", 4086f),
                DemoDrive("}wuuF`wkUM`GAZIpDCbBAL?`@A^AVAn@CdBCfB?DATI|EAZAj@SAyAGq@CUA]A_CKS?QAiAEC?A?KA[AQ?KAI?GA_@AE?y@Em@CmAGc@AcAGsGUWA[Ac@AcDMYAqEQ[A]A[Cc@AiCKUAwEQS?OAMAG?K?[A}BIU?@c@FaE@q@?GC[CEEMEMa@`@w@x@UR_@`@YXGDIHIHQRSTs@t@IJoArAEDUTg@^}CbDUTU?ECEGq@iBQc@kAsCaAcCKY{A{ECGGQEDA@g@ZOJEBEAI@GBC?KFE@KHGBA@ABQNCHC@}@j@cAj@UNIYo@mBEUAK?I@K@I|@w@FG@QEOEMk@mAO]O]e@aAACGOQ_@JKhBgBHGH@\\dAHJNf@\\tAb@rBX~AJhADh@TzBTpBRnAP`AD`@@FBA@?@?@?@@@?hA~BHNFLFJFLFJFLFLZl@JRh@dA@B?FABABCBC@C@A?", 4059f),
                DemoDrive("y}yuFfotUC`B?VSCsCI[CO?C??WDsC@M?GQAKAcBGyAEOAmDOQAEYO_A_@aCE[EUE[AEGc@G]AEo@cESuAKm@CK_@aCGa@EYEYCYAU?[B{@?Y@s@@]AOAGFeCHwEh@wVPwID_BBq@@a@B_@FoC?I?G@a@DwCDuB@a@@W?EFoCBoA@m@@k@Aa@?m@@k@?m@LuFRkKVeKFkEBgADaDBcA@y@BUBOFQJSFGNQtEyEfCmCzAwA@AFGh@g@dAgAHKLOFEb@e@HKJKFId@i@BCr@u@JM|@cADClBsBf@i@BCTUBBDDB@DBD@B?H?DAD?FEBABCBEBEBEBG@G@E?I?G?IAGCGAEACCGACAAGEECCAGAEAI@E?EBC@M_@CC]u@i@mAi@qASg@Oc@So@Qg@Qg@Qg@IUw@{BJKvAwAjAiAfAgAj@k@p@u@dAeA|@w@NO{@eBM[IYo@mBEUAK?I@K@I|@w@FG@QEOEMk@mAO]O]e@aAACGOQ_@JKhBgBHGH@\\dAHJNf@\\tAb@rBX~AJhADh@TzBTpBRnAP`AD`@@FBA@?@?@?@@@?hA~BHNFLFJFLFJFLFLZl@JRh@dA@B?FABABCBC@C@A?", 5519f),
            ),
        ),
    )
}
