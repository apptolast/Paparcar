package com.rndeveloper.paparcar.fakes.data

/**
 * Las calles del mundo de demostracion, con trayectos que van POR LA CALZADA.
 * [MOCK-A-DEMO-ROUTE-MUST-FOLLOW-THE-STREETS-001]
 *
 * GENERADO por `mockkit/tools/fetch_demo_routes.py` — no editar a mano. Las polilineas son
 * respuestas de OSRM sobre el grafo real de Dublin, en el mismo formato precision-5 que lee
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
            name = "Dame Street",
            endLat = 53.344133,
            endLon = -6.267019,
            approaches = listOf(
                DemoDrive("ynudIxube@r@A\\ALCHx@Jv@LbAJ@N@LD~Ad@HBEl@ARAFAHMlBSfCY`Ek@bIEf@Cf@SjCQtCCZSfCCh@Eb@I`AEl@CVOdCEn@I|@ItAQ~BCVCZCXYpDCRAFG^pAhA^^JAp@j@JJl@h@jAfABBVTFPB@@@VRDBRN??B@XDv@r@VR\\ZZZpBlBZZVTfA~@lAhAZ`@h@r@d@n@|BbD~@hALZBDh@h@^^`@`@PPBBXVbBzARRLJvAfAj@d@x@t@v@r@XZjAlAPP~DpEZ^LNDDFF??LL`CvBJH??FDBB@@?@hCzBDFFFNNDDRTNR@@DFRXZn@Xx@TbARnAJbAFx@?H@F@TJlABfA@lAFX?nA?J?H@\\E\\VH@@@?B@XHt@BN@|@BnA@L?pAFz@@l@F\\DD?L@H@`@Jh@PB@@@B@B@HBhAX^N@@lAf@VLHDLFNFd@N\\JJDHDDBHDFDNHTLFB`@RLFRFf@HPBD?VANOFE\\e@FIHUPm@F[P{AJg@BKPy@BU@[Fi@Fk@J_Ah@aFHy@@K?G@a@Ey@KcBEs@AIEYK[EKKUOKQIQIIECGCEAE?CI_@i@kFWkBGk@Eg@AOEcAC]?K", 4325f),
                DemoDrive("gsndI~bee@GZMGYL]PGBf@nD@HBN_@LEBG@EBQFkB|@EBw@d@kAz@]Zw@t@QPQPEDKLCDIJa@p@]n@CDYh@OXMTINe@v@U^KRCFGJIJ]h@}@tAA@k@x@_@p@GJINGND|@Dx@DbABp@?V?Ja@FkC\\IJKBG@e@LKG_@PG@M@K?I?]Ao@AC?I?C?oAEU?o@CI?E?G?C?i@H{@NGBG@o@P]HmATKBGBOB_@FOBSDWBODUFe@NmA\\IBG?I?{B]o@Ke@G]E]EEAq@K}@Mi@IUEoB]EAA?OECAMKCEW_@cAgAUQMIUKUI_@Ia@Em@GC?A?C?G?sCHcA?@nAApE?j@?`@@r@@V?X@L@X@B", 2314f),
                DemoDrive("mtrdIzcpe@EIOe@EQG_@AMFYNc@Z_BNg@LW^q@JGJGLGLEDCNGIoACk@M_CSgFEkA?Q?K?QAk@Cq@AOA]?e@GuCCo@?u@AaA?s@?eCDoE?m@@aCDuF@eB?I?Q@c@@_C?c@@qC@s@?uAB_H@yA?K?C?C@G?S?S@qA?K?Q?U@[B}A@K?_@@g@@a@?C?GB}@FoC@e@BaA?UFy@@IFg@Fg@BOBMDWD[?CBK@KBK@K@EBUVcBPsAFa@NcAFa@RyA@KHaADmA@c@@kAR_Q@{BD{B?]@S@YD_BB[Bc@CG@CD}@N}BNyCJuBBW@O@MBUDc@`@kDBQFg@LkAHu@XcCBK@O\\yC@I@WB_@FgA?I@I@a@NFd@N\\JJDHDDBHDFDNHTLFB`@RLFRFf@HPBD?VANOFE\\e@FIHUPm@F[P{AJg@BKPy@BU@[Fi@Fk@J_Ah@aFHy@@K?G@a@Ey@KcBEs@AIEYK[EKKUOKQIQIIECGCEAE?CI_@i@kFWkBGk@Eg@AOEcAC]?K", 3527f),
            ),
        ),
        DemoStreet(
            name = "Capel Street",
            endLat = 53.346483,
            endLon = -6.268057,
            approaches = listOf(
                DemoDrive("g~udI~ybe@O|@CNWt@?H@F@DBDJJJHHDH@D?HHDDFFPk@N]JQHIBEx@o@LMLOPOLOV_@r@qABEPU@CDGl@y@FIDGhA}Af@s@l@aADIHWRs@V_AFUH[FON[RB`@RFDz@`@LHRJVNHDRLr@p@X^RXZ`@JLNPh@v@JNZd@d@|@Zn@d@~@h@fAFLDFBD\\n@Zl@Tf@d@|@HPfAhBBBJTJRFJ@DFJBBJVFL@D|@tB`@bAXr@Rh@b@hADJL^RWDGtAqBDGFMp@aAR[DEDGBEt@gADIfBiCj@w@NUFKDGLOHJDBDFZ\\TXPRdEpE|@jAVXFHDDLLDFDHj@r@DFl@h@f@`@JHTLt@b@PLHCB?D?@@B@B@HBLFz@^v@`@VL~@d@HBNJDBB@@@B@LHHDDDHFBBBDLNLTNRFHJHLFNFH@N@B?J@D?L?LGJCLANAX?JHD@B@NBJBPB@??@RBj@Jf@JLHDBFDHH@BBB@H@F?@?LAr@ArBAv@AbAAdAA`AAtA?F?H?L?b@A`@?t@AhA@X?PBf@@XBb@@TDn@BV@JBZ@L@LHn@NvARvA@H@NHd@D\\DVBNEACCE@EBC@iAf@A?A@OFSFGBqBp@EBA?KB@V@HFfA\\bHHzAPvCDr@@l@BPBHBBD?rBg@NCp@dIFn@PbCLzA\\bEBTPtB", 3914f),
                DemoDrive("yaodIlkee@IDW^A@IDh@dEHf@DZBLHp@NhA@XNpCVjI@|@?`@?Z?B?HAJ?ZMAC?G?E?E?C?E@G@I@E?e@H[DG@G@E@gATMDc@HOAC@gAJIH[BCCuATG@SB@\\?X@|A@l@FxEJ`G?RK@C@u@Dg@D{@FoBPWB{ANwBRWBkAJc@D}@JE@E?QBAL?B@RRvI@|@?RALGVANSlDG|AAPAJA`@wB_A{@_@GC_@KQESCYCS?O@WBkALK?E@[@G@G?g@@Y?o@SMCg@Mo@UQKMEa@IK?QBKHM@G?I@I@E?E@G?C@A?YB]AkBGSAO?Y?oAAMGqAaACAECEEOSQ@S@SCO?I@O@c@Dy@EQ?]BG@K@G@]J{A\\E@GAA?OBM@C?A@_@Be@DK@E@KDAe@CgACy@Eo@KoAMiBIkAUyCMqBGcAAI?IA]Aa@KkDEs@OsDmBj@LzA\\bEBTPtB", 3338f),
                DemoDrive("cksdItape@AWCaAIcAGe@Ie@Qi@Oe@i@qBKw@HgBTsBFwAAUA_@I_@EWISKSrBuGdBsF@E~@yCx@kC\\kA\\cAHMKOCKAK@K@KDKFGFAH@FD~AiGf@kBjHyXCQv@{CLc@Ne@?U@[B}A@K?_@@g@@a@?C?GB}@FoC@e@BaA?UFy@@IFg@Fg@BOBMDWD[?CBK@KBK@K@EBUVcBPsAFa@NcAFa@RyA@KHaADmA@c@@kAR_Q@{BD{B?]@S@YD_BB[Bc@CG@CD}@N}BNyCJuBBW@O@MBUDc@`@kDBQFg@LkAHu@XcCBK@O\\yC@I@WB_@FgA?I@I@a@@W@OL{CTyFFmB?uAAi@Ae@CgACy@Eo@KoAMiBIkAUyCMqBGcAAI?IA]Aa@KkDEs@OsDmBj@LzA\\bEBTPtB", 3637f),
            ),
        ),
        DemoStreet(
            name = "Baggot Street",
            endLat = 53.335890,
            endLon = -6.247741,
            approaches = listOf(
                DemoDrive("u_mdIfiae@h@KNxC@Re@XCBC?g@DWEICGCGGIIGOQc@Og@M_@KUCAKKGEGCC?C?E@EDED]^GHIHKJKFIBG@G@M?E?G?K@QBEBCBC@CBCDCDAHE\\ADIj@]Ou@WSKc@WECCAUUOOOQwAjBg@v@CBQVc@n@EFW\\CDeAxACDC@ABEFSXe@l@U\\EFORMPCFa@n@i@x@Yf@i@v@gBdCwAnBQXo@~@kAdBABQT]j@GNCFGLEJANANADEPQj@Oh@?@CHADML]YGCECICu@YUIYIMGGCaAYGCMEQGEAKESGMEGCSGCAKEo@Ua@MOEOGGAa@OOEIEKCICGCICICKCCAECGASIA?MEGCCAOEOGGAq@U[MCAQGq@UMEyAe@CAEAIGGGI^CH[jAm@pBABELy@dBS\\U^a@n@A@ABIJKZIJ{AjBSRy@tAmApB]l@", 2313f),
                DemoDrive("mcqdI~ole@A@`@xCsBlAK@KM?Ag@}@CAAEa@k@QAMe@_@_BG_@c@mCG[EYEEEEIc@AIe@mDOoAYaCKo@CSGg@AIAGAGCSEg@?_@?CE[Io@KWAEGWEWEWCSAEAKASCi@Am@Ce@CiA?]AK?WAYC}@EyB@SBUAcB?}@?a@BkADgABg@Du@?MBm@DoBBg@?M?KBmAD{ADsA?GHaB@OD]D[DY@E@KFk@TmBLoAFm@Fu@@e@@i@@wA@s@?M?K?[?k@?WAQAWIo@AOGc@UsBGa@KUCOGa@Ow@Iw@Ea@Cc@Ca@Ao@BU@[Fi@Fk@J_Ah@aFHy@@K?G@a@PARELBd@`@b@\\NHZJf@?lA@vABx@BJAD?D?HAXCL?JA\\FJ?`@HLDPJn@Tf@LLB`@EFAHAZCHAJk@BIBIH_@@GBKh@uCJs@Fg@Fk@Fy@LmCJg@^{@`@iAH_@Fa@Fu@Fq@@k@DuB@{BCm@Ag@?YEW?g@@sD@mDAoD?Q@W?I?KBSPm@v@oEx@wE@GJi@ZoAXcBF]\\sBFq@JkACSEUCGGIO[m@g@s@i@uAgA_As@MKEEGGECECGGBQBO@GF_@@GDc@n@{DDS@M@EAC?CCCaBiAIMd@oCPyAFm@Fo@PsA@_@D]BMNm@HYv@wCv@wCBGDUDQDM@EFMBCn@kADKZi@hAoBb@u@LT]l@", 4352f),
            ),
        ),
        DemoStreet(
            name = "Camden Street",
            endLat = 53.333975,
            endLon = -6.265230,
            approaches = listOf(
                DemoDrive("uosdI|gbe@TXvAhBTXFFLSBC?A@ABEPWHMZg@NQXe@T]R]FGLQPW@C@A@A@CFK~AaCZe@DGNWJM@ADGDGFGDCTEV@rD^P@D?D@V@F@@?@?F@fAJd@DV@RBt@FF@B?@?@?RBJ@FCJEpEd@D?DEDCDIBSDiA@S@I@EBIDIBCFCJ?B?D?`@Hj@JNB`@DrC^B?RBRBD@B?@?`@FZDRBhEh@D?D@RBRBD@D@dAJLBd@Dd@FRBdAN`@FB?`@DB?EjACv@EhAAN?PNDLBd@HHBJHRRVX@?JFJDBBpAlAPPx@r@rAjADDJH[pAUbAENI^]vAU|@CLI\\NNDB@@@@??DD@@`B~A@@xBxBNPPNpBtB\\\\|AzANNPNLLzFxFJJ`D~CJJhBhBnClCv@t@h@h@DDHHBBPRPNBFDFJLLNHFFHDJFj@@X?T?NA\\C\\Gl@Gr@E^KdAYjCAH?HAB_@|DEj@A`@Ab@ClB?\\AVAR?D?LEpCAPAx@?f@@P?L?^?R?BDhC?hAB`B?h@?H?R@d@?P@v@?J@n@D|@Dx@DbABp@?V?Ja@FkC\\IJKBG@e@LKGUJ", 3838f),
                DemoDrive("etldIrvde@m@NiAVG@@VDhCK@w@FI@}Cz@}C|@]F]FWHI@UuBMsAAIAMAWKF]Lg@TKDMFKDo@\\SHGBSHSBm@?M?E?m@Ck@EC?MAm@Iw@Mm@GWE_@EeAMK@IBK_@G[Ma@e@{AMOGGIGECA?OCK?EBcAdA_@b@YZMPKJEFCFIJEHQ\\MVO`@Ql@_@hB]`BSp@ERILGHOJKFKFOB]Fk@Fe@Fe@J_@LEBG@EBQFkB|@EBw@d@kAz@]Zw@t@QPQPEDKLCDIJa@p@]n@CDYh@OXMTINe@v@U^KRCFGJIJ]h@}@tAA@k@x@_@p@GJINGND|@Dx@DbABp@?V?Ja@FkC\\IJKBG@e@LKGUJ", 2276f),
                DemoDrive("mwpdIn~oe@cBVW@?m@@q@@u@?kABuA@qBDoE@eABeB?qAD{B?K?A@]@Y@MToFDy@VcFRmE@O@e@@[FmAHoAPwD^iI@U@K?MAUFKJIFGJGHCp@Oz@SPEHCFA@?DAFCB?~@STEl@Mh@K\\CD?F??C?E@E@E@C@CBCDAB?B?@@HWFUNg@DUDWJq@RuABKBMPsADSNeAF]DWBSF]TeB^iCF]T_B@KPeADQFUL[FIRYl@cALQNUjAiBp@kA`@o@NYLOLQDGLU@ABEf@qAN_@|@kC^_ANg@BMDaA@OA_C?gA?k@@_@BYJ_ATyA@MDUT{ARwAfAiH@I@KD]Fi@@IXkCLgAVeC@K@I?E@I@IDm@@I@YAe@AIACGq@Km@c@gCAEI_@AKCK[gBUuBESGk@Y}BIs@S_BEYCQOiAE[A_@AY?o@@s@@eB@cABsB@qA@iAAk@A]OaBGu@?I?AAK?YEsBEyBGiDAc@GcD?SKaGGyEAm@A}A?YA]a@FkC\\IJKBG@e@LKGUJ", 3857f),
            ),
        ),
        DemoStreet(
            name = "Wexford Street",
            endLat = 53.336927,
            endLon = -6.265601,
            approaches = listOf(
                DemoDrive("ubtdI`jbe@MPk@`AOVQZORADCDINDFR\\\\h@@B@@FJFJDFNVd@v@PZNV\\j@DDRXvArBX\\NPXXNPFHJSDGBGBEDI@C^g@T[z@oAT[HMT]z@qA|@qAlC}DLSBC?A@ABEPWHMZg@NQXe@T]R]FGLQPW@C@A@A@CFK~AaCZe@DGNWJM@ADGDGFGDCTEV@rD^P@D?D@V@F@@?@?F@fAJd@DV@RBt@FF@B?@?@?RBJ@FCJEpEd@D?DEDCDIBSDiA@S@I@EBIDIBCFCJ?B?D?`@Hj@JNB`@DrC^B?RBRBD@B?@?`@FZDRBhEh@D?D@RBRBD@D@dAJLBd@Dd@FRBdAN`@FB?`@DB?EjACv@EhAAN?PNDLBd@HHBJHRRVX@?JFJDBBpAlAPPx@r@rAjADDJH[pAUbAENI^]vAU|@CLI\\NNDB@@@@??DD@@`B~A@@xBxBNPPNpBtB\\\\|AzANNPNLLzFxFJJ`D~CJJhBhBnClCv@t@h@h@DDHHBBPRPNBFDFJLLNHFFHDJFj@@X?T?NA\\C\\Gl@Gr@E^KdAYjCAH?HAB_@|DEj@A`@Ab@ClB?\\AVAR?D?LEpCAPAx@?f@@P?L?^?R?BDhC?hAB`B?h@?H?R@d@?P@v@?J@n@D|@Dx@DbABp@?V?Ja@FkC\\IJKBG@e@LKG_@PG@M@K?I?]Ao@AC?I?C?oAEU?o@CI?E?G?C?i@H{@NGBG@o@P]HaAP", 4789f),
                DemoDrive("sfmdIlyde@o@PKFKF]Lg@TKDMFKDo@\\SHGBSHSBm@?M?E?m@Ck@EC?MAm@Iw@Mm@GWE_@EeAMK@IBK_@G[Ma@e@{AMOGGIGECA?OCK?EBcAdA_@b@YZMPKJEFCFIJEHQ\\MVO`@Ql@_@hB]`BSp@ERILGHOJKFKFOB]Fk@Fe@Fe@J_@LEBG@EBQFkB|@EBw@d@kAz@]Zw@t@QPQPEDKLCDIJa@p@]n@CDYh@OXMTINe@v@U^KRCFGJIJ]h@}@tAA@k@x@_@p@GJINGND|@Dx@DbABp@?V?Ja@FkC\\IJKBG@e@LKG_@PG@M@K?I?]Ao@AC?I?C?oAEU?o@CI?E?G?C?i@H{@NGBG@o@P]HaAP", 2151f),
                DemoDrive("yjqdIn}oe@??ABC@C?Y?K??MCSEGCb@?h@?lA?`@?XBXDd@DX@DL~@Hj@LbAHv@Fv@B^@ZFbAFt@CLELCHEHwBLa@DG?C@O@WBOqFw@aYCi@EsAEgACm@QkDKyAWsCAGCWM{@Iy@AKEk@@U@QFiA@m@Ew@Ia@[kAuC_KSqAYkBe@gGq@aICc@Gq@Gw@MyBIwBWiGEy@Ak@QqFCsA?aAEmA?KGoAE}@Eg@QoBAMC]EYESGOCCCESKYMWMCCKGCCYSSQQUa@WQQEKCM?SNcAFa@RyA@KHaADmA@c@@kAR_Q@{BD{B?]@S@YD_BB[Bc@CG@CD}@N}BNyCJuBBW@O@MBUDc@`@kDBQFg@LkAHu@XcCBK@O\\yC@I@WB_@FgA?I@I@a@NFd@N\\JJDHDDBHDFDNHTLFB`@RLFRFf@HPBD?VANOFE\\e@FIHUPm@F[P{AJg@BKPy@BU@[Fi@Fk@J_Ah@aFHy@@K?G@a@Ey@KcBEs@AIEYK[EKKUDY?CBM@M@ED?DA|@SDAJ?PBN@d@Jh@JF@r@FNDZB?c@@Y?m@KeABM\\OXO`C{AJGGk@`Aw@d@i@FIBIFKBI?[?C?A@Az@INMA]DuALcC@Yd@Fn@JzB\\H?F?HClA]d@OTGNEVCRENC^GNCFCJCJC", 4927f),
            ),
        ),
        DemoStreet(
            name = "Pearse Street",
            endLat = 53.342600,
            endLon = -6.239976,
            approaches = listOf(
                DemoDrive("ghudInh}d@BK^uA?I?GIKKI[lAGV_@`BIb@ADCJAJCH_@zBOv@]xCCJAFCTCPEd@ARAb@ANANGbACdBDPAf@AJ?LK`@MnAE^EZIn@E\\ObAALE\\Ar@ANMpA_@~DCPYhCCVQhBAN?@AFAFGLI|AAFAREt@C\\Cr@OpCAXEz@AHAJEz@?j@?L?FBpAHtAPpBHr@d@|CHf@DV@FDVF`@ZxAb@pBJf@Np@VtAl@tCH`@FRFLNZPFRZFJj@v@H^z@`Aj@r@^d@n@v@PVJLFFJLDFNPRXFFTZDDBDDDHLJHDFDDf@f@DDz@|@\\f@DDRVPPDDJLLLVVJL\\ZXZZ\\PRV\\v@jADFDHHJp@fAPVPZd@x@PXHN`AdB~@~A@@DHJRDFR\\\\h@@B@@FJFJDFNVd@v@PZNV\\j@DDRXvArBX\\NPXXNPFHJSDGBGBEDI@C^g@T[z@oAT[HMT]z@qA|@qAlC}DLSBC?A@ABEPWHMZg@NQXe@T]R]FGLQPW@C@A@A@CFK~AaCZe@DGNWJM@ADGDGFGDCTEV@rD^P@D?D@V@F@@?@?F@fAJd@DV@RBt@FF@B?@?@?RBJ@FCJEpEd@D?DEDCDIBSDiA@S@I@EBIDIBCFCJ?B?D?`@Hj@JNB`@DrC^B?RBRBD@B?@?`@FZDRBhEh@D?D@RBBa@@E?GVyD", 4199f),
                DemoDrive("yindI|y_e@[dBIb@ATm@GgAMg@GEAA?IAKCSC_D_@_@BIBMBKFABGNAFGNABMDGRCHM`@M^ABSl@KVMX]I_@IUA_@AG?KAg@Aq@AC?wCGmDQWC[EO?YCa@Ak@BU@[FG@i@LE@_@JWHwBl@]HkBd@ODQHGBWPYVSTi@r@U\\aAvAGF}@pAc@n@U^S\\QZA@Sj@Ul@a@tAWfAYpAKf@CHAHIb@Kf@e@dCOt@?@?BA@Ox@ERKj@CJ?@CH?@ERAFETGX[zAKb@??Oj@]lA[dA]lAKXSl@IVA@CFGNCFIKCAII_@OUEgBSw@GC?a@EC?a@GeAOSCe@Ge@EMCeAKEAEASCBa@@E?GVyD", 2304f),
                DemoDrive("mprdI~~je@B_CCQDO?Q?M@_A@]?_@BuA?E?G@G@EBAN@B?B?@?@@B?D@??F@lAHl@DB?J?@{BD{B?]@S@YD_BB[Bc@CG@CD}@N}BNyCJuBBW@O@MBUDc@`@kDBQFg@LkAHu@XcCBK@O\\yC@I@WB_@FgA?I@I@a@MGIEWMmAg@AA_@OiAYICCACAAACAi@Qa@KIAMAE?]Em@G{@AqAGM?oAA}@COAu@CYICAA?AAWII_@?QAc@?uAFOAmACgAKmAAUAG?IGy@KcASoAUcAYy@[o@SYEGAAOSSUEEOOGGEGiC{B?AAACCGE??KIaCwBMM??GGEEMO[_@_EqEQQkAmAY[w@s@y@u@k@e@wAgAMKSScB{AYWCCQQa@a@_@_@i@i@CAUIeB}BoAaB}AmBW]y@y@uAuA@I@MBYReBr@yFF]T{AFi@PgBJ}@NcAZcCh@{ETiBFa@Hs@n@_F`@kDBWD[Jo@b@oD@IBQ@MFe@Hc@Li@Te@j@y@DIBCPYRWDGtAqBDGFMp@aAR[DEDGBEt@gADIfBiCj@w@NUFKDGLOJSDGBGBEDI@C^g@T[z@oAT[HMT]z@qA|@qAlC}DLSBC?A@ABEPWHMZg@NQXe@T]R]FGLQPW@C@A@A@CFK~AaCZe@DGNWJM@ADGDGFGDCTEV@rD^P@D?D@V@F@@?@?F@fAJd@DV@RBt@FF@B?@?@?RBJ@FCJEpEd@D?DEDCDIBSDiA@S@I@EBIDIBCFCJ?B?D?`@Hj@JNB`@DrC^B?RBRBD@B?@?`@FZDRBhEh@D?D@RBBa@@E?GVyD", 5630f),
            ),
        ),
        DemoStreet(
            name = "Thomas Street",
            endLat = 53.343471,
            endLon = -6.284686,
            approaches = listOf(
                DemoDrive("ijudItbfe@^mCT}ABOXoBb@sC@?F[H_@BOFPB@@@VRDBRN??B@XDv@r@VR\\ZZZpBlBZZVTfA~@lAhAZ`@h@r@d@n@|BbD~@hALZBDh@h@^^`@`@PPBBXVbBzARRLJvAfAj@d@x@t@v@r@XZjAlAPP~DpEZ^LNDDFF??LL`CvBJH??FDBB@@?@hCzBDFFFNNDDRTNR@@DFRXZn@Xx@TbARnAJbAFx@?H@F@TJlABfA@lAFX?nA?J?H@\\E\\VH@@@?B@XHt@BN@|@BnA@L?pAFz@@l@F\\DD?L@H@`@Jh@PB@@@B@B@HBhAX^N@@lAf@VLHDLFNFd@N\\JJDHDDBHDFDNHTLFB`@RLFRFf@HPBD?VANOFE\\e@FIHUPm@F[b@qABIFOHKFCJ?H~@Fr@R`B@N?ZF`@TrBFb@@NHn@@V@P?V?j@?Z?J?LAr@AvAAh@Ad@Gt@Gl@MnAUlBGj@AJADEXEZE\\ANI`B?FErAEzAClA?J?LCf@EnBCl@?LAR", 3423f),
                DemoDrive("yondIxqhe@j@|@HNBFDDGHe@r@EHeAxAILMPOf@CJCPEECCSQCAIGu@o@GEWUq@i@WQEEUOGEWSIEWOOGICA??AIAEAGACAIAeAOICG?OAIAGAWCSAYCWAcAIs@Cw@Cw@B_@BW@wADe@BG?GAOEi@S]KEAOEQGe@MECGA[MMG[GKGg@e@m@q@w@s@MKMKECkA}@WOWMwAk@wB_A{@_@GC_@KQESCYCS?O@WBkALK?E@[@G@G?g@@Y?o@SMCg@Mo@UQKMEa@IK?QBKHM@G?I@I@E?E@G?C@A?YB]AkBGSAO?Y?oAAMGWAm@[KEI@GFEBSZ]~CWnCM`AC^?d@?H?J?R@^H~@Fr@R`B@N?ZF`@TrBFb@@NHn@@V@P?V?j@?Z?J?LAr@AvAAh@Ad@Gt@Gl@MnAUlBGj@AJADEXEZE\\ANI`B?FErAEzAClA?J?LCf@EnBCl@?LAR", 2750f),
                DemoDrive("esrdIr|se@d@DRRJZDQNi@jBmIrByJJu@Ha@@[AYESMYUUa@WYKYKWIQQGSCS?WFe@J]HKHMHIPMp@WTSNSDMJ_@Ba@Ac@Gk@Ic@Kc@QWUMi@OYGSKKIKQEWAS@I?GBSDOLOTQTQPWL]FW@[Am@G{@G{@AeA@cABmBGsBMcDMaCG}AMgCKoBKuCI_COsFAg@A]Ko@Yk@EG]_@[c@[i@[e@O[Oe@EQG_@AMFYNc@Z_BNg@LW^q@JGJGLGLEDCNGIoACk@M_CSgFEkA?Q?K?QAk@Cq@AOA]?e@GuCCo@?u@AaA?s@?eCDoE?m@@aCDuF@eB?I?Q@c@@_C?c@@qC@s@?uAB_H@yA?K?C?C@G?S?S@qA?K?Q?U@[B}A@K?_@@g@@a@?C?GB}@FoC@e@BaA?UFy@@IFg@Fg@BOBMDWD[?CBK@KBK@K@EBUVcBPsAFa@NcAFa@RyA@KHaADmA@c@@kAR_Q@{BD{B?]JFt@XNFJBJBfBp@NFjA`@TFn@TD@NDNDR@rBAT?V?xA@P@Ba@", 4120f),
            ),
        ),
        DemoStreet(
            name = "Aungier Street",
            endLat = 53.341437,
            endLon = -6.265407,
            approaches = listOf(
                DemoDrive("k~tdIxibe@Yz@M`@JLNPh@v@JNZd@d@|@Zn@d@~@h@fAFLDFBD\\n@Zl@Tf@d@|@HPfAhBBBJTJRFJ@DFJBBJVFL@D|@tB`@bAXr@Rh@b@hADJL^QXCBEHk@x@Ud@Mh@Ib@Gd@ALCPAHc@nDKn@EZCVa@jDo@~EIr@G`@UhBi@zE[bCObAK|@QfBGh@UzAG\\s@xFSdBCXfA~@lAhAZ`@h@r@d@n@|BbD~@hALZBDh@h@^^`@`@PPBBXVbBzARRLJvAfAj@d@x@t@v@r@XZjAlAPP~DpEZ^LNDDFF??LL`CvBJH??FDBB@@?@hCzBDFFFNNDDRTNR@@DFRXZn@Xx@TbARnAJbAFx@?H@F@TJlABfA@lAFX?nA?J?H@\\E\\VH@@@?B@XHt@BN@|@BnA@L?pAFz@@l@F\\DD?L@H@`@Jh@PB@@@B@B@HBhAX^N@@lAf@VLHDLFNFd@N\\JJDHDDBHDFDNHTLFB`@RLFRFf@HPBD?VANOFE\\e@FIHUPm@F[P{AJg@BKPy@BU@[Fi@Fk@J_Ah@aFHy@@K?G@a@Ey@KcBEs@AIEYK[EKKUDY?CBM@M@ED?DA|@SDAJ?PBN@d@Jh@JF@?W?UAOGk@u@sDJIZWx@_Aj@q@l@y@Xc@t@eABIU[QYGMEKKYCGCIIe@AMCS?S@]@QAC@K@KBQFB", 4614f),
                DemoDrive("ycndIxude@m@n@_@b@YZMPKJEFCFIJEHQ\\MVO`@Ql@_@hB]`BSp@ERILGHOJKFKFOB]Fk@Fe@Fe@J_@LEBG@EBQFkB|@EBw@d@kAz@]Zw@t@QPQPEDKLCDIJa@p@]n@CDYh@OXMTINe@v@U^KRCFGJIJ]h@}@tAA@k@x@_@p@GJINGND|@Dx@DbABp@?V?Ja@FkC\\IJKBG@e@LKG_@PG@M@K?I?]Ao@AC?I?C?oAEU?o@CI?E?G?C?i@H{@NGBG@o@P]HmATKBGBOB_@FOBSDWBODUFe@NmA\\IBG?I?{B]o@Ke@G]E]EEAq@K}@Mi@IUEoB]EAA?GA", 2092f),
                DemoDrive("edrdIz`pe@Bz@Bx@\\EBd@?ZPAB@DbAFL@T\\AEsBEqACcAY_OKoDIQIMQYc@o@CQAo@AwACa@?S?Oj@CD?\\ABA??@?H?HAVAb@CPAv@Ef@CTA~AERWHUFa@F]BMPy@@U@QFiA@m@Ew@Ia@[kAuC_KSqAYkBe@gGq@aICc@Gq@Gw@MyBIwBWiGEy@Ak@QqFCsA?aAEmA?KGoAE}@Eg@QoBAMC]EYESGOCCCESKYMWMCCKGCCYSSQQUa@WQQEKCM?SNcAFa@RyA@KHaADmA@c@@kAR_Q@{BD{B?]@S@YD_BB[Bc@CG@CD}@N}BNyCJuBBW@O@MBUDc@`@kDBQFg@LkAHu@XcCBK@O\\yC@I@WB_@FgA?I@I@a@NFd@N\\JJDHDDBHDFDNHTLFB`@RLFRFf@HPBD?VANOFE\\e@FIHUPm@F[P{AJg@BKPy@BU@[Fi@Fk@J_Ah@aFHy@@K?G@a@Ey@KcBEs@AIEYK[EKKUDY?CBM@M@ED?DA|@SDAJ?PBN@d@Jh@JF@?W?UAOGk@u@sDJIZWx@_Aj@q@l@y@Xc@t@eABIU[QYGMEKKYCGCIIe@AMCS?S@]@QAC@K@KBQFB", 4345f),
            ),
        ),
        DemoStreet(
            name = "Clanbrassil Street",
            endLat = 53.331183,
            endLon = -6.275223,
            approaches = listOf(
                DemoDrive("e~rdItfde@AGEEA?C?YNYPy@`@A@??GB?RLrA?N?JFh@Fn@@JFd@HEFE|AaAn@_@b@YX[JIFEDGHIDEC[CYA[?Y@[@Q@W@GD[FQH[JWJQ@CBEBC@CJOLMHMHGLGJCLANAX?JHD@B@NBJBPB@??@RBj@Jf@J\\?DADADE@CDGBGFMDM@K@KHuARoCXaERuCB]Hk@FS@CDELIHDZJ~@\\HBJDr@VZJB@FBB@NH\\Pn@Zl@ZJDDBbAj@TLDBDBFBB@JJHFDBB@@@JFDBb@RLF\\PFHFB@@B@PHHDTLPHp@^bAh@z@b@L@JBDADEFO@KJGDCBAFAF?VR~@t@FFBBLJLc@H]ZgAz@kD~AmGDUPo@Ni@ZqAJ_@ZgANNDB@@@@??DD@@`B~A@@xBxBNPPNpBtB\\\\|AzANNPNLLzFxFJJ`D~CJJhBhBnClCv@t@h@h@DDHHBBPRPNBFDFJLLNHFFHDJFj@@X?T?NA\\C\\Gl@Gr@E^KdAYjCAH?HAB_@|DEj@A`@Ab@ClB?\\AVAR?D?LEpCAPAx@?f@@P?L?^?R?BDhC?hAB`B?h@?H?R@d@?P@v@?J@n@D|@Dx@DbABp@?V?J@\\?X@|A@l@FxEJ`G?RFbD@b@FhDDxBDrB?X@J?@?HFt@N`B@\\@j@AhAApACrBAbAAdBAr@^Cv@Cv@Br@BN@", 4448f),
                DemoDrive("obldIzvfe@mAf@EBEJGOCG@OCYMeAEW]N]LYFS?{AOE?GAEAqBi@QEA?ECgAm@u@]a@KMCOCWCc@@G?Q@UDi@R{@^yAn@yCzAKFyAt@QHWLQJUFYHKKYUECOGKEm@EQAQAAAG?EA[CYEIAMCQEWCsAMKAi@GSCa@E_@GG?UEgBSIAy@I}@M}C[GAu@IWCUCOCiAKKAQCg@EEAG?[EQCy@IMAUCuBU]Es@Gi@GKAQAMAC?G?E?E?C?E@G@I@E?e@H[DG@G@E@gATMDc@HOAC@gAJIH[BCCuATG@SB@\\?X@|A@l@FxEJ`G?RFbD@b@FhDDxBDrB?X@J?@?HFt@N`B@\\@j@AhAApACrBAbAAdBAr@^Cv@Cv@Br@BN@", 2838f),
                DemoDrive("yepdI~{qe@uA`CLPR`@BFZv@BX@TOj@[pAEJEN?V?H?L?JBN@J@HCXX`@d@t@\\f@Zd@FHHNDHFFT\\`@l@rArB^h@LPTTXVHHNARNB?B?B?BA@A@A@ABCBG@E@I?I@o@AIGo@McB?KCOIcAOoBUiCGgAGcA]_GMkC_@{F]yFG{Ai@qKAIAQy@_O[cFMqBASAKAKMsBM_COiCKcB_@{FO}CEm@GcBAq@EmAEeAAK?c@?GAWHUFWJ_@@CXgABKDSJe@BODQd@qBv@wCjA{D~DqNp@aC\\uA?YNy@@C@GBQFSH[DS@EFUFIf@cBn@yBxBmKp@gDNw@Nu@F]BKDODWZaB^gCJq@Dc@R{BNkBH_ABc@Fw@NoB`@iFh@eHDe@ToCd@gGDi@B[RmDVkDNmBVqDL}AHeAFy@PmCJsBPoDBg@Bk@Be@DeAJmC?M@a@OGICA??AIAEAGACAIAeAOICG?OAIAGAWCSAYCWAs@G", 4451f),
            ),
        ),
    )

    val transit: List<DemoStreet> = listOf(
        DemoStreet(
            name = "Broombridge Road",
            endLat = 53.372999,
            endLon = -6.299922,
            approaches = listOf(
                DemoDrive("ef{dIh`ie@Et@GpI?JfBFfADz@@j@BzAFL?@OBOFKHGHAH@FHFJBN@NH?t@@B?nDJvCF`@@`ADN?H?F?RAh@Gt@Yb@WDEf@a@LMtAiAPIj@OjASDAFANCLCp@]VQLM\\[X_@NY^u@P]DKf@y@X[LOTSDCFEf@WLENCJCNE^?LAL@L?F?`@?R@F?P?h@CVGDA~@OREb@Id@EhABdBJn@Dl@FRBD?VBA`@?Ho@jUMpEAVRDb@Jr@Nr@Nn@Lv@PTD^H\\H@x@@p@@F@l@@N@XHnAF|@@b@@`@Ch@C^Kh@GXKf@CLCVCTAJCl@@h@Dj@Ff@Lp@Lj@XfAHZRx@BFFd@HAb@G^Mj@Wl@c@z@u@d@_@p@_@d@QJCTIB`@@R?b@TvD?BPlC@P@X?f@A\\AHAZIn@If@Qr@W|@On@Mj@M`ACh@Er@?nAFjCBdBHbDF`CB~@BpAJvAH~@B`@D`A@`A?zAAL?v@CrBCjD?H?xA?t@@pA@x@?ZDvAJrE@hBA~BElAInAKrAEl@SrAUzA_@pBo@`DQ`AIx@Gp@EjAAVBpABHBD@DJHF@PAzBeCbAiAZ[d@_@d@]p@c@^Yf@_@j@a@x@m@\\UVQVMd@Qf@QPELA^E`@CPADAXC~@Mz@K`@CV@ND`@Nl@V^LJBLDP?L???FAF?HATCh@Ib@Ib@OTMFC`@U\\WZUX_@Xm@DKf@uAIMCEyBeCGID_@Fq@Ak@E_@So@Uc@QQMGSEUISEMEICiDw@iAQ@yB@w@@_@ViCoBcAOGOCmA?S@E?W@I?U@U@E?U?ICGA", 6086f),
                DemoDrive("ghtdInpke@Ee@MsAKqAMOECIAIAi@z@EFQZINg@dAi@dAa@~@Qb@}@dCQf@Yx@Ob@[|@_AdCo@|ASf@GL]bAWt@c@tAIVa@rAIZCH]xAUt@_@vBUlA_@`BSp@EJIHC@C@G@KE_@?E?E?S@]B{@P[JM@MF_AToAZe@Pc@Pi@Zg@ZC@?@?@@F@FA@?DADCBC@E?CACECDKLa@p@aBvC{@vA_@f@_@^}@v@aAz@cAv@m@`@GBc@T_@REB?F?D?JCHCFC@C@C@C?ECCCCGAGAG?E?G@E@E@CBEBADA?SD}DIAQ?aDIaAAy@EUAOA]?u@Co@C]Ki@S_B_AiBgAoBcAOGOCmA?S@E?W@I?U@U@E?U?ICGA", 2369f),
                DemoDrive("ahxdI~wve@LeAP}AVuBZiBXsABKLo@Ps@v@kD`@uARe@JWn@cBn@oBV_AbBsG~A}GXeAt@eDh@{BNs@jCcLNq@BINo@R_A|@qDp@cDD]BUAIAQ?KBKBIFKDEDALYLWDILMh@_CDST{@Lm@\\uALi@FUDUPq@VgAT_AJe@Nm@BI`BwHr@gDh@eCLi@@IFS@IXoABIFWdAuEJ_@VcAF[f@yBz@oDDORiAV{At@oFFg@Hg@@KFi@DU@El@qEl@gET_BHk@TsA`@oBPu@BG@GRy@DUDMVgA@KPq@R}@T}@Ry@XoAb@iBLk@f@sBZqAf@cCH[H_@U[IKEI]m@}@aBMWi@aAu@kBk@wB]mA]wACKCKKe@s@_DaAgECKC@C?ECCCCGAGAG?E?G@E@E@CBEBADA?SD}DIAQ?aDIaAAy@EUAOA]?u@Co@C]Ki@S_B_AiBgAoBcAOGOCmA?S@E?W@I?U@U@E?U?ICGA", 4201f),
            ),
        ),
        DemoStreet(
            name = "Cabra Road",
            endLat = 53.360978,
            endLon = -6.281011,
            approaches = listOf(
                DemoDrive("kyxdIrnee@G@g@REHAH?FBRe@R[LSFG?IAEAEAg@NJp@NbB?fCIpAGp@Mh@ILELHVJnAJnA?^?\\AZE\\EXk@|BYlAOz@EZALAL?J?B?F@D?NhABB?fCHl@@`A?TCJCFA@?ZI~C{@ZIDAFCFAHAXCBAjDk@dBWJCD?FAhAOzAOv@Td@NDBFBH@JDr@VvEbBTFF@bCb@^@\\BD?F@h@DH@J@F?RBHBD@FBBBLHRPFHFHRRLN@@FHBBHHLPBBBBjAzA~BxCTZ\\f@BD@BXj@BDB@t@v@VVXPVPTJzBpAB@@BHD|@l@PJXPVLDDbAf@|At@@@JBJDDB^Lj@VdDfBRHFDp@b@d@BN@`@DTBT@V@X@L?B?F@D?D?F@B@XJFDb@LXLXJ\\LNDlAd@JFbA^NFLDNFEd@K~AE`@E^]nEYlDEp@Ed@IvAF@bBXH@n@JZFTD~ATpBXH@@?J@Aj@?L?LCjAKpG?JAB?H?J?F?FIlGAr@", 3592f),
                DemoDrive("o}qdIhzge@H~B?H?@?D@T?TGfBIhCOlEMzDCd@AJ?HEd@IEKE]Ke@OOGMGIEWMmAg@AA_@OiAYICCACAAACAi@Qa@KIAMAE?]Em@G{@AqAGM?oAA}@COAu@CYICAA?AAWIW?A?AAAAmAi@IEQIc@WCAcAm@o@c@c@]IMQGa@KuA[GAgAUg@I}@OKAMCI?[Ae@JGBIBQJGBK?EBIFKFQJWNGDG@?@QBi@DWBM@qCJsAFo@Bw@AU?UAg@EUC]Ce@EEAMAQCoAMEAGAE?o@SOEOEECYKOEc@Uc@Uc@UUGGAGAMAIAo@?Q?Q?Q?]?M?U?Q@AX?`E@V?fB@bC?D?pA?HA`BAbACxBAj@?L?LCjAKpG?JAB?H?J?F?FIlGAr@", 2633f),
                DemoDrive("qwudItnse@LKFEJEh@iAzAeDfAgBdAw@h@WRMFClBy@d@[t@k@d@u@x@gBhAsCZw@DKTi@f@GHCDOFKHEHAFBHDvAuEtAiEhDwKl@oB|C{J|CuJ|BqHh@cBxFmQnC}InCwIrBuGdBsF@E~@yCx@kC\\kA\\cAHMKOCKAK@K@KEMO[q@aBe@{@c@m@SU]OICg@SKI]G[IQMGKCEEOEEIKGGEM?KAM@OP[FKLYPg@Pm@DWDO@AFq@BWNcANiAH{@@K@O@O?IA]CO?OCICIKQEGEGkA{BaCqE}AsC]m@e@{@aDeGU_@EIEGSa@We@}@cBGIMUQ[GIaA_B_A_Ba@s@Wi@GUG[E_@MqAMsAKqAC[AIE[Is@AKIu@Ky@UoBAGQuAEWAIAKY{BY{BQwA]aCMs@cAyFIa@Mm@k@eDKHqA`ASNu@h@c@\\q@^]FO@qBBA?IAD_DBkC?Y", 4665f),
            ),
        ),
        DemoStreet(
            name = "Navan Road",
            endLat = 53.367578,
            endLon = -6.317159,
            approaches = listOf(
                DemoDrive("mazdI~mle@Zc@fAyAP[Xr@Ld@HZLp@FZD\\JV@BDLFR@@DLLObAsAdB}Br@_ALOT]HMrGiJp@_AHMXa@j@cAj@iAZq@rBgEj@gA\\c@X[l@k@~@s@DEFCd@_@JGtAaAj@Sr@Ul@Ev@Cd@AVADAD?@?h@IHAb@G^Mj@Wl@c@z@u@d@_@p@_@d@QJCTIB`@@R?b@TvD?BPlC@P@X?f@A\\AHAZIn@If@Qr@W|@On@Mj@M`ACh@Er@?nAFjCBdBHbDF`CB~@BpAJvAH~@B`@D`A@`A?zAAL?v@CrBCjD?H?xA?t@@pA@x@?ZDvAJrE@hBA~BElAInAKrAEl@SrAUzA_@pBo@`DQ`AIx@Gp@EjAAVBpABHBD@DJHF@PAzBeCbAiAZ[d@_@d@]p@c@^Yf@_@j@a@x@m@\\UVQVMd@Qf@QPELA^E`@CPADAXC~@Mz@K`@CV@ND`@Nl@V^LJBLDP?L???FAF?HATCh@Ib@Ib@OTMFC`@U\\WZUX_@Xm@DKf@uAd@}Ah@_BBGBG^_AVm@Xc@\\g@JOJKAGAG?E?G@E@E@CBEBADAD?B@BB@BBD@D?F?D?JCHCFC@C@BJ`AfEr@~CJd@BJBJ\\vA\\lAj@vBt@jBh@`ALV|@`B\\l@DHHJTZI^IZg@bC[pAg@rBMj@c@hBYnASx@U|@S|@Qp@AJWfAELETSx@AFCFQt@a@nBUrAIj@U~Am@fE_@lC", 5388f),
                DemoDrive("wesdIh{ne@??MWM_@Eo@Ce@Ga@Qg@MS@E~@yCx@kC\\kA\\cAHMKOCKAK@K@KEMO[q@aBe@{@c@m@SU]OICg@SKI]G[IQMGKCEEOEEIKGGEM?KAM@OP[FKLYPg@Pm@DWDO@AFq@BWNcANiAH{@@K@O@O?IA]CO?OCICIKQEGEGkA{BaCqE}AsC]m@e@{@aDeGU_@EIEGSa@We@}@cBGIMUQ[GIaA_B_A_Ba@s@Wi@GUG[E_@MqAMsAKqAMOECIAIAi@z@EFQZINg@dAi@dAa@~@Qb@}@dCQf@Yx@Ob@[|@_AdCo@|ASf@GL]bAWt@c@tAIVa@rAIZCH]xAUt@_@vBUlA_@`BSx@GTGRADMb@MZGRQz@u@dDI\\i@xBWhAi@jBAFM`@MPEP_@vAIZOj@Qn@_A|DI\\CJCJo@nCaCdKCH]zA_@|AMl@Ib@I^IZg@bC[pAg@rBMj@c@hBYnASx@U|@S|@Qp@AJWfAELETSx@AFCFQt@a@nBUrAIj@U~Am@fE_@lC", 3865f),
                DemoDrive("ijwdIhbze@??@L?LELIFE@]@MBOB@p@Ap@Cr@Ej@Gf@AP@T?JAHCFCDEBE?EAEGCG?K?I@IEBC@E?EAWIWEOEAAOICAMIEEIGMMOOCAQSAAGGEGGGKOMQCGGKEKACOWAGCEGOAEAIIW?CIYKe@CKAECKK[KYGQGOMc@OUKOIMEEQSQO_@YWU][CCKIIGGEMKIIA?UQCCCCOKGGGEQMQMIGGCKIOMUQGEQMOKOMGEIGMKQKKGGCGCGAECE?AAE?KAG?G@K@OBC@SBKBUDC?SDI@K@G@I@g@DG?WBm@?i@BW@_@@Y@A?Q@K?M?G@c@B[@ICAEAE?G@EJw@PmABO@I@K?ADY@GBOBSFi@BM@EFc@?CBIHm@NiARyABWL{@WMUOUQKDSMGEGa@][Vw@|D}Mh@uBr@oCBIdAgEh@_Ch@uBP{@v@uDh@aCR_AfBkIzAeHjAgFlDgPvBoJ~A}GXeAt@eDh@{BNs@jCcLNq@BINo@R_A|@qDp@cDD]BUAIAQ?KBKBIFKDEDALYLWDILMh@_CDST{@Lm@\\uALi@FUDUPq@VgAT_AJe@Nm@BI`BwHr@gDh@eCLi@@IFS@IXoABIFWdAuEJ_@VcAF[f@yBz@oDDORiAV{At@oFFg@Hg@@KFi@DU@ELcA", 4567f),
            ),
        ),
    )
}
