const assetPathPrefix = ".";
const imgIconCog = `../nav/a3818.svg`;
const imgIconUpload = `../nav/62afc.svg`;
const imgIconCube = `../nav/beebf.svg`;
const imgIconSliders = `../nav/b4ac6.svg`;
const imgIconChevronRight = `${assetPathPrefix}/722b1.svg`;
const imgIconFlask = `${assetPathPrefix}/80a95.svg`;
const imgIconNotes = `${assetPathPrefix}/894de.svg`;
const imgIconOthers = `${assetPathPrefix}/36cb1.svg`;
const imgIconMultimaterial = `${assetPathPrefix}/63b7a.svg`;
const imgIconSupportPaint = `${assetPathPrefix}/596aa.svg`;
const imgIconSpeed = `${assetPathPrefix}/8c05e.svg`;
const imgIconStrength = `${assetPathPrefix}/90279.svg`;
const imgIconQuality = `${assetPathPrefix}/9aef1.svg`;
const imgIconChevronDown = `${assetPathPrefix}/2b977.svg`;
const imgStateOn = `${assetPathPrefix}/d5526.svg`;
const imgIconAtom = `${assetPathPrefix}/b0a2f.svg`;
const imgIconLayers = `${assetPathPrefix}/a5ce7.svg`;
const imgIconRefresh = `${assetPathPrefix}/7ded1.svg`;
const imgIconPlus = `${assetPathPrefix}/563d4.svg`;
const imgIconSpool = `${assetPathPrefix}/fd7e5.svg`;
const imgIconPrinter = `${assetPathPrefix}/dfbf9.svg`;
const imgIconSliders1 = `../nav/78e0b.svg`;
const imgIconSearch = `${assetPathPrefix}/99bfe.svg`;
const imgGlow = `${assetPathPrefix}/e15f9.svg`;
const imgGlow1 = `${assetPathPrefix}/e02fa.svg`;
const imgGlow2 = `${assetPathPrefix}/4f3e4.svg`;
const imgIconWifi = `${assetPathPrefix}/94f5d.svg`;
const imgEllipse = `${assetPathPrefix}/65a57.svg`;

function IconCog({ className }: { className?: string }) {
  return (
    <div className={className || "relative size-[24px]"} data-node-id="2:57" data-name="icon/cog">
      <img alt="" className="absolute block inset-0 max-w-none size-full" src={imgIconCog} />
    </div>
  );
}

function IconUpload({ className }: { className?: string }) {
  return (
    <div className={className || "relative size-[24px]"} data-node-id="2:52" data-name="icon/upload">
      <img alt="" className="absolute block inset-0 max-w-none size-full" src={imgIconUpload} />
    </div>
  );
}

function IconCube({ className }: { className?: string }) {
  return (
    <div className={className || "relative size-[24px]"} data-node-id="2:37" data-name="icon/cube">
      <img alt="" className="absolute block inset-0 max-w-none size-full" src={imgIconCube} />
    </div>
  );
}

type NavBarProps = {
  className?: string;
  active?: "Settings";
};

function NavBar({ className, active = "Settings" }: NavBarProps) {
  return (
    <div className={className || "backdrop-blur-[10px] bg-[var(--glass\\/fill,rgba(255,255,255,0.11))] border border-[var(--glass\\/stroke,rgba(255,255,255,0.2))] border-solid content-stretch flex gap-[4px] h-[68px] items-start p-[6px] relative rounded-[34px] w-[358px]"} data-node-id="2:255">
      <div className="content-stretch flex flex-[1_0_0] flex-col gap-[3px] h-full items-center justify-center min-w-px overflow-clip relative rounded-[28px]" data-node-id="2:256" data-name="Workspace">
        <IconCube className="relative shrink-0 size-[22px]" />
        <p className="[word-break:break-word] font-['Manrope:SemiBold'] font-semibold leading-[1.3] relative shrink-0 text-[11px] text-[color:var(--text\/primary,white)] whitespace-nowrap" data-node-id="2:259">
          Workspace
        </p>
      </div>
      <div className="bg-[var(--surface\/active,white)] content-stretch flex flex-[1_0_0] flex-col gap-[3px] h-full items-center justify-center min-w-px overflow-clip relative rounded-[28px]" data-node-id="2:260" data-name="Settings">
        <div className="relative shrink-0 size-[22px]" data-node-id="2:261" data-name="icon/sliders">
          <img alt="" className="absolute block inset-0 max-w-none size-full" src={imgIconSliders} />
        </div>
        <p className="[word-break:break-word] font-['Manrope:SemiBold'] font-semibold leading-[1.3] relative shrink-0 text-[11px] text-[color:var(--text\/on-active,#16181c)] whitespace-nowrap" data-node-id="2:265">
          Settings
        </p>
      </div>
      <div className="content-stretch flex flex-[1_0_0] flex-col gap-[3px] h-full items-center justify-center min-w-px overflow-clip relative rounded-[28px]" data-node-id="2:266" data-name="Export">
        <IconUpload className="relative shrink-0 size-[22px]" />
        <p className="[word-break:break-word] font-['Manrope:SemiBold'] font-semibold leading-[1.3] relative shrink-0 text-[11px] text-[color:var(--text\/primary,white)] whitespace-nowrap" data-node-id="2:269">
          Export
        </p>
      </div>
      <div className="content-stretch flex flex-[1_0_0] flex-col gap-[3px] h-full items-center justify-center min-w-px overflow-clip relative rounded-[28px]" data-node-id="2:270" data-name="Preferences">
        <IconCog className="relative shrink-0 size-[22px]" />
        <p className="[word-break:break-word] font-['Manrope:SemiBold'] font-semibold leading-[1.3] relative shrink-0 text-[11px] text-[color:var(--text\/primary,white)] whitespace-nowrap" data-node-id="2:275">
          Preferences
        </p>
      </div>
    </div>
  );
}

function IconChevronRight({ className }: { className?: string }) {
  return (
    <div className={className || "relative size-[24px]"} data-node-id="2:67" data-name="icon/chevron-right">
      <img alt="" className="absolute block inset-0 max-w-none size-full" src={imgIconChevronRight} />
    </div>
  );
}

function IconFlask({ className }: { className?: string }) {
  return (
    <div className={className || "relative size-[24px]"} data-node-id="2:209" data-name="icon/flask">
      <img alt="" className="absolute block inset-0 max-w-none size-full" src={imgIconFlask} />
    </div>
  );
}

function IconNotes({ className }: { className?: string }) {
  return (
    <div className={className || "relative size-[24px]"} data-node-id="2:206" data-name="icon/notes">
      <img alt="" className="absolute block inset-0 max-w-none size-full" src={imgIconNotes} />
    </div>
  );
}

function IconOthers({ className }: { className?: string }) {
  return (
    <div className={className || "relative size-[24px]"} data-node-id="2:203" data-name="icon/others">
      <img alt="" className="absolute block inset-0 max-w-none size-full" src={imgIconOthers} />
    </div>
  );
}

function IconMultimaterial({ className }: { className?: string }) {
  return (
    <div className={className || "relative size-[24px]"} data-node-id="2:198" data-name="icon/multimaterial">
      <img alt="" className="absolute block inset-0 max-w-none size-full" src={imgIconMultimaterial} />
    </div>
  );
}

function IconSupportPaint({ className }: { className?: string }) {
  return (
    <div className={className || "relative size-[24px]"} data-node-id="2:154" data-name="icon/support-paint">
      <img alt="" className="absolute block inset-0 max-w-none size-full" src={imgIconSupportPaint} />
    </div>
  );
}

function IconSpeed({ className }: { className?: string }) {
  return (
    <div className={className || "relative size-[24px]"} data-node-id="2:195" data-name="icon/speed">
      <img alt="" className="absolute block inset-0 max-w-none size-full" src={imgIconSpeed} />
    </div>
  );
}

function IconStrength({ className }: { className?: string }) {
  return (
    <div className={className || "relative size-[24px]"} data-node-id="2:192" data-name="icon/strength">
      <img alt="" className="absolute block inset-0 max-w-none size-full" src={imgIconStrength} />
    </div>
  );
}

function IconQuality({ className }: { className?: string }) {
  return (
    <div className={className || "relative size-[24px]"} data-node-id="2:188" data-name="icon/quality">
      <img alt="" className="absolute block inset-0 max-w-none size-full" src={imgIconQuality} />
    </div>
  );
}

function IconChevronDown({ className }: { className?: string }) {
  return (
    <div className={className || "relative size-[24px]"} data-node-id="2:64" data-name="icon/chevron-down">
      <img alt="" className="absolute block inset-0 max-w-none size-full" src={imgIconChevronDown} />
    </div>
  );
}

type SwitchProps = {
  className?: string;
  state?: "On";
};

function Switch({ className, state = "On" }: SwitchProps) {
  return (
    <div className={className || "h-[28px] relative w-[46px]"} data-node-id="2:224">
      <img alt="" className="absolute block inset-0 max-w-none size-full" src={imgStateOn} />
    </div>
  );
}

function IconAtom({ className }: { className?: string }) {
  return (
    <div className={className || "relative size-[24px]"} data-node-id="2:215" data-name="icon/atom">
      <img alt="" className="absolute block inset-0 max-w-none size-full" src={imgIconAtom} />
    </div>
  );
}

function IconLayers({ className }: { className?: string }) {
  return (
    <div className={className || "relative size-[24px]"} data-node-id="2:40" data-name="icon/layers">
      <img alt="" className="absolute block inset-0 max-w-none size-full" src={imgIconLayers} />
    </div>
  );
}

function IconRefresh({ className }: { className?: string }) {
  return (
    <div className={className || "relative size-[24px]"} data-node-id="2:93" data-name="icon/refresh">
      <img alt="" className="absolute block inset-0 max-w-none size-full" src={imgIconRefresh} />
    </div>
  );
}

function IconPlus({ className }: { className?: string }) {
  return (
    <div className={className || "relative size-[24px]"} data-node-id="2:73" data-name="icon/plus">
      <img alt="" className="absolute block inset-0 max-w-none size-full" src={imgIconPlus} />
    </div>
  );
}

function IconSpool({ className }: { className?: string }) {
  return (
    <div className={className || "relative size-[24px]"} data-node-id="2:185" data-name="icon/spool">
      <img alt="" className="absolute block inset-0 max-w-none size-full" src={imgIconSpool} />
    </div>
  );
}

function IconPrinter({ className }: { className?: string }) {
  return (
    <div className={className || "relative size-[24px]"} data-node-id="2:81" data-name="icon/printer">
      <img alt="" className="absolute block inset-0 max-w-none size-full" src={imgIconPrinter} />
    </div>
  );
}

function IconSliders({ className }: { className?: string }) {
  return (
    <div className={className || "relative size-[24px]"} data-node-id="2:49" data-name="icon/sliders">
      <img alt="" className="absolute block inset-0 max-w-none size-full" src={imgIconSliders1} />
    </div>
  );
}

function IconSearch({ className }: { className?: string }) {
  return (
    <div className={className || "relative size-[24px]"} data-node-id="2:77" data-name="icon/search">
      <img alt="" className="absolute block inset-0 max-w-none size-full" src={imgIconSearch} />
    </div>
  );
}

export default function Settings() {
  return (
    <div className="bg-[var(--bg\/base,#383d46)] overflow-clip relative rounded-[44px] size-full" data-node-id="6:368" data-name="Settings">
      <div className="absolute h-[920px] left-[-430px] top-[-460px] w-[860px]" data-node-id="6:369" data-name="Glow">
        <img alt="" className="absolute block inset-0 max-w-none size-full" src={imgGlow} />
      </div>
      <div className="absolute h-[930px] left-[40px] top-0 w-[700px]" data-node-id="6:370" data-name="Glow">
        <img alt="" className="absolute block inset-0 max-w-none size-full" src={imgGlow1} />
      </div>
      <div className="absolute h-[760px] left-[-290px] top-[460px] w-[700px]" data-node-id="6:371" data-name="Glow">
        <img alt="" className="absolute block inset-0 max-w-none size-full" src={imgGlow2} />
      </div>
      <div className="absolute content-stretch flex gap-[12px] h-[48px] items-center left-[20px] overflow-clip top-[52px] w-[350px]" data-node-id="6:376" data-name="Top bar">
        <div className="content-stretch flex flex-[1_0_0] flex-col items-start min-w-px overflow-clip relative" data-node-id="6:377" data-name="Frame">
          <p className="[word-break:break-word] font-['Manrope:ExtraBold'] font-extrabold leading-[1.3] relative shrink-0 text-[24px] text-[color:var(--text\/primary,white)] whitespace-nowrap" data-node-id="6:378">
            Settings
          </p>
        </div>
        <div className="backdrop-blur-[10px] bg-[var(--glass\/fill,rgba(255,255,255,0.11))] border border-[var(--glass\/stroke,rgba(255,255,255,0.2))] border-solid content-stretch flex items-center justify-center overflow-clip relative rounded-[22px] shrink-0 size-[44px]" data-node-id="6:372" data-name="Icon button · search">
          <IconSearch className="relative shrink-0 size-[22px]" />
        </div>
      </div>
      <div className="absolute content-stretch flex flex-col h-[732px] items-start left-0 overflow-x-clip overflow-y-auto pb-[120px] px-[20px] top-[112px] w-[390px]" data-node-id="6:379" data-name="Scroll content">
        <div className="backdrop-blur-[10px] bg-[var(--glass\/fill,rgba(255,255,255,0.11))] border border-[var(--glass\/stroke,rgba(255,255,255,0.2))] border-solid content-stretch flex flex-col items-start overflow-clip relative rounded-[24px] shrink-0 w-full" data-node-id="6:389" data-name="Card · Printer">
          <div className="content-stretch flex gap-[8px] items-center overflow-clip pb-[4px] pl-[16px] pr-[8px] pt-[8px] relative shrink-0 w-full" data-node-id="6:390" data-name="Frame">
            <IconPrinter className="relative shrink-0 size-[20px]" />
            <p className="[word-break:break-word] flex-[1_0_0] font-['Manrope:Bold'] font-bold leading-[1.3] min-w-px relative text-[16px] text-[color:var(--text\/primary,white)]" data-node-id="6:394">
              Printer
            </p>
            <div className="content-stretch flex items-center justify-center overflow-clip relative rounded-[22px] shrink-0 size-[44px]" data-node-id="6:380" data-name="Icon button · wifi">
              <div className="relative shrink-0 size-[22px]" data-node-id="6:381" data-name="icon/wifi">
                <img alt="" className="absolute block inset-0 max-w-none size-full" src={imgIconWifi} />
              </div>
            </div>
            <div className="content-stretch flex items-center justify-center overflow-clip relative rounded-[22px] shrink-0 size-[44px]" data-node-id="6:384" data-name="Icon button · sliders">
              <IconSliders className="relative shrink-0 size-[22px]" />
            </div>
          </div>
          <div className="content-stretch flex flex-col items-start overflow-clip pb-[12px] pt-[4px] px-[12px] relative shrink-0 w-full" data-node-id="6:406" data-name="Frame">
            <a className="bg-[var(--field\/fill,rgba(0,0,0,0.22))] border border-[var(--field\/stroke,rgba(255,255,255,0.18))] border-solid content-stretch cursor-pointer flex gap-[12px] items-center overflow-clip px-[12px] py-[10px] relative rounded-[18px] shrink-0 w-full" data-node-id="6:400" data-name="Picker · Bambu Lab H2S">
              <div className="bg-[var(--glass\/fill,rgba(255,255,255,0.11))] content-stretch flex items-center justify-center overflow-clip relative rounded-[12px] shrink-0 size-[44px]" data-node-id="6:396" data-name="Thumb">
                <IconPrinter className="relative shrink-0 size-[24px]" />
              </div>
              <div className="[word-break:break-word] content-stretch flex flex-[1_0_0] flex-col gap-px items-start leading-[1.3] min-w-px overflow-clip relative text-left whitespace-nowrap" data-node-id="6:401" data-name="Frame">
                <p className="font-['Manrope:SemiBold'] font-semibold relative shrink-0 text-[15px] text-[color:var(--text\/primary,white)]" data-node-id="6:402">
                  Bambu Lab H2S
                </p>
                <p className="font-['Manrope:Medium'] font-medium relative shrink-0 text-[12px] text-[color:var(--text\/secondary,rgba(255,255,255,0.88))]" data-node-id="6:403">
                  Nozzle 0.4 · Hardened steel
                </p>
              </div>
              <IconChevronDown className="relative shrink-0 size-[20px]" />
            </a>
          </div>
        </div>
        <div className="h-[10px] relative shrink-0 w-px" data-node-id="6:395" data-name="Frame" />
        <div className="backdrop-blur-[10px] bg-[var(--glass\/fill,rgba(255,255,255,0.11))] border border-[var(--glass\/stroke,rgba(255,255,255,0.2))] border-solid content-stretch flex flex-col items-start overflow-clip relative rounded-[24px] shrink-0 w-full" data-node-id="6:413" data-name="Card · Filament">
          <div className="content-stretch flex gap-[8px] items-center overflow-clip pb-[4px] pl-[16px] pr-[8px] pt-[8px] relative shrink-0 w-full" data-node-id="6:414" data-name="Frame">
            <IconSpool className="relative shrink-0 size-[20px]" />
            <p className="[word-break:break-word] flex-[1_0_0] font-['Manrope:Bold'] font-bold leading-[1.3] min-w-px relative text-[16px] text-[color:var(--text\/primary,white)]" data-node-id="6:418">
              Filament
            </p>
            <div className="content-stretch flex items-center justify-center overflow-clip relative rounded-[22px] shrink-0 size-[44px]" data-node-id="6:407" data-name="Icon button · plus">
              <IconPlus className="relative shrink-0 size-[22px]" />
            </div>
            <div className="content-stretch flex items-center justify-center overflow-clip relative rounded-[22px] shrink-0 size-[44px]" data-node-id="6:410" data-name="Icon button · refresh">
              <IconRefresh className="relative shrink-0 size-[22px]" />
            </div>
          </div>
          <div className="content-stretch flex flex-col gap-[8px] items-start overflow-clip pb-[12px] pt-[4px] px-[12px] relative shrink-0 w-full" data-node-id="6:438" data-name="Frame">
            <a className="bg-[var(--field\/fill,rgba(0,0,0,0.22))] border border-[var(--field\/stroke,rgba(255,255,255,0.18))] border-solid content-stretch cursor-pointer flex gap-[12px] items-center overflow-clip px-[12px] py-[10px] relative rounded-[18px] shrink-0 w-full" data-node-id="6:423" data-name="Picker · PETG HF">
              <div className="bg-[#0f1114] border border-[var(--text\/primary,white)] border-solid content-stretch flex items-center justify-center overflow-clip relative rounded-[7px] shrink-0 size-[24px]" data-node-id="6:420" data-name="Slot 1">
                <p className="[word-break:break-word] font-['Manrope:ExtraBold'] font-extrabold leading-[1.3] relative shrink-0 text-[12px] text-[color:var(--text\/primary,white)] text-left whitespace-nowrap" data-node-id="6:421">
                  1
                </p>
              </div>
              <div className="bg-[#e9eef5] relative rounded-[4px] shrink-0 size-[14px]" data-node-id="6:422" data-name="Rectangle" />
              <div className="[word-break:break-word] content-stretch flex flex-[1_0_0] flex-col gap-px items-start leading-[1.3] min-w-px overflow-clip relative text-left whitespace-nowrap" data-node-id="6:424" data-name="Frame">
                <p className="font-['Manrope:SemiBold'] font-semibold relative shrink-0 text-[15px] text-[color:var(--text\/primary,white)]" data-node-id="6:425">
                  PETG HF
                </p>
                <p className="font-['Manrope:Medium'] font-medium relative shrink-0 text-[12px] text-[color:var(--text\/secondary,rgba(255,255,255,0.88))]" data-node-id="6:426">
                  Bambu Lab
                </p>
              </div>
              <IconChevronDown className="relative shrink-0 size-[20px]" />
            </a>
            <div className="bg-[var(--field\/fill,rgba(0,0,0,0.22))] border border-[var(--field\/stroke,rgba(255,255,255,0.18))] border-solid content-stretch flex gap-[12px] items-center overflow-clip px-[12px] py-[10px] relative rounded-[18px] shrink-0 w-full" data-node-id="6:432" data-name="Picker · PLA Basic">
              <div className="bg-[#0f1114] border border-[var(--text\/primary,white)] border-solid content-stretch flex items-center justify-center overflow-clip relative rounded-[7px] shrink-0 size-[24px]" data-node-id="6:429" data-name="Slot 2">
                <p className="[word-break:break-word] font-['Manrope:ExtraBold'] font-extrabold leading-[1.3] relative shrink-0 text-[12px] text-[color:var(--text\/primary,white)] whitespace-nowrap" data-node-id="6:430">
                  2
                </p>
              </div>
              <div className="bg-[#1c1c1e] border border-[var(--text\/primary,white)] border-solid relative rounded-[4px] shrink-0 size-[14px]" data-node-id="6:431" data-name="Rectangle" />
              <div className="[word-break:break-word] content-stretch flex flex-[1_0_0] flex-col gap-px items-start leading-[1.3] min-w-px overflow-clip relative whitespace-nowrap" data-node-id="6:433" data-name="Frame">
                <p className="font-['Manrope:SemiBold'] font-semibold relative shrink-0 text-[15px] text-[color:var(--text\/primary,white)]" data-node-id="6:434">
                  PLA Basic
                </p>
                <p className="font-['Manrope:Medium'] font-medium relative shrink-0 text-[12px] text-[color:var(--text\/secondary,rgba(255,255,255,0.88))]" data-node-id="6:435">
                  Bambu Lab
                </p>
              </div>
              <IconChevronDown className="relative shrink-0 size-[20px]" />
            </div>
          </div>
        </div>
        <div className="h-[10px] relative shrink-0 w-px" data-node-id="6:419" data-name="Frame" />
        <div className="backdrop-blur-[10px] bg-[var(--glass\/fill,rgba(255,255,255,0.11))] border border-[var(--glass\/stroke,rgba(255,255,255,0.2))] border-solid content-stretch flex flex-col items-start overflow-clip relative rounded-[24px] shrink-0 w-full" data-node-id="6:439" data-name="Card · Process">
          <div className="content-stretch flex gap-[8px] items-center overflow-clip pb-[4px] pl-[16px] pr-[8px] pt-[8px] relative shrink-0 w-full" data-node-id="6:440" data-name="Frame">
            <IconLayers className="relative shrink-0 size-[20px]" />
            <p className="[word-break:break-word] flex-[1_0_0] font-['Manrope:Bold'] font-bold leading-[1.3] min-w-px relative text-[16px] text-[color:var(--text\/primary,white)]" data-node-id="6:443">
              Process
            </p>
            <div className="content-stretch flex gap-[8px] items-center overflow-clip pr-[6px] relative shrink-0" data-node-id="6:445" data-name="Frame">
              <IconAtom className="relative shrink-0 size-[18px]" />
              <p className="[word-break:break-word] font-['Manrope:SemiBold'] font-semibold leading-[1.3] relative shrink-0 text-[12px] text-[color:var(--text\/secondary,rgba(255,255,255,0.88))] whitespace-nowrap" data-node-id="6:451">
                Advanced
              </p>
              <Switch className="h-[28px] relative shrink-0 w-[46px]" />
            </div>
          </div>
          <div className="content-stretch flex items-center justify-between overflow-clip pb-[10px] pt-[6px] px-[16px] relative shrink-0 w-full" data-node-id="6:454" data-name="Frame">
            <div className="bg-[var(--field\/fill,rgba(0,0,0,0.22))] content-stretch flex items-start overflow-clip p-[3px] relative rounded-[16px] shrink-0" data-node-id="6:455" data-name="Scope">
              <div className="bg-[var(--surface\/active,white)] content-stretch flex h-[32px] items-center justify-center overflow-clip px-[12px] relative rounded-[13px] shrink-0" data-node-id="6:456" data-name="Global">
                <p className="[word-break:break-word] font-['Manrope:Bold'] font-bold leading-[1.3] relative shrink-0 text-[13px] text-[color:var(--text\/on-active,#16181c)] whitespace-nowrap" data-node-id="6:457">
                  Global
                </p>
              </div>
              <div className="content-stretch flex h-[32px] items-center justify-center overflow-clip px-[12px] relative rounded-[13px] shrink-0" data-node-id="6:458" data-name="Objects">
                <p className="[word-break:break-word] font-['Manrope:Bold'] font-bold leading-[1.3] relative shrink-0 text-[13px] text-[color:var(--text\/primary,white)] whitespace-nowrap" data-node-id="6:459">
                  Objects
                </p>
              </div>
            </div>
            <div className="content-stretch flex gap-[6px] items-center overflow-clip relative shrink-0" data-node-id="6:460" data-name="Frame">
              <div className="relative shrink-0 size-[8px]" data-node-id="6:461" data-name="Ellipse">
                <img alt="" className="absolute block inset-0 max-w-none size-full" src={imgEllipse} />
              </div>
              <p className="[word-break:break-word] font-['Manrope:Medium'] font-medium leading-[1.3] relative shrink-0 text-[12px] text-[color:var(--text\/secondary,rgba(255,255,255,0.88))] whitespace-nowrap" data-node-id="6:462">
                3 changed
              </p>
            </div>
          </div>
          <div className="content-stretch flex flex-col items-start overflow-clip pb-[12px] px-[12px] relative shrink-0 w-full" data-node-id="6:469" data-name="Frame">
            <a className="bg-[var(--field\/fill,rgba(0,0,0,0.22))] border border-[var(--field\/stroke,rgba(255,255,255,0.18))] border-solid content-stretch cursor-pointer flex gap-[12px] items-center overflow-clip px-[12px] py-[10px] relative rounded-[18px] shrink-0 w-full" data-node-id="6:463" data-name="Picker · 0.20mm Standard @BBL H2S">
              <div className="[word-break:break-word] content-stretch flex flex-[1_0_0] flex-col gap-px items-start leading-[1.3] min-w-px overflow-clip relative text-left whitespace-nowrap" data-node-id="6:464" data-name="Frame">
                <p className="font-['Manrope:SemiBold'] font-semibold relative shrink-0 text-[15px] text-[color:var(--text\/primary,white)]" data-node-id="6:465">
                  0.20mm Standard @BBL H2S
                </p>
                <p className="font-['Manrope:Medium'] font-medium relative shrink-0 text-[12px] text-[color:var(--text\/secondary,rgba(255,255,255,0.88))]" data-node-id="6:466">
                  System preset · modified
                </p>
              </div>
              <IconChevronDown className="relative shrink-0 size-[20px]" />
            </a>
          </div>
          <a className="border-[var(--glass\/divider,rgba(255,255,255,0.12))] border-solid border-t content-stretch cursor-pointer flex gap-[12px] items-center min-h-[52px] overflow-clip pl-[16px] pr-[14px] py-[8px] relative shrink-0 w-full" data-node-id="6:478" data-name="Row · Quality">
            <div className="bg-[var(--glass\/fill,rgba(255,255,255,0.11))] content-stretch flex items-center justify-center overflow-clip relative rounded-[10px] shrink-0 size-[32px]" data-node-id="6:475" data-name="Row icon">
              <IconQuality className="relative shrink-0 size-[18px]" />
            </div>
            <div className="content-stretch flex flex-[1_0_0] flex-col items-start min-w-px overflow-clip relative" data-node-id="6:479" data-name="Frame">
              <div className="content-stretch flex items-center overflow-clip relative shrink-0 w-full" data-node-id="6:480" data-name="Frame">
                <p className="[word-break:break-word] flex-[1_0_0] font-['Manrope:SemiBold'] font-semibold leading-[1.3] min-w-px relative text-[15px] text-[color:var(--text\/primary,white)] text-left" data-node-id="6:481">
                  Quality
                </p>
              </div>
            </div>
            <div className="content-stretch flex gap-[6px] items-center overflow-clip relative shrink-0" data-node-id="6:470" data-name="Frame">
              <div className="relative shrink-0 size-[8px]" data-node-id="6:471" data-name="Ellipse">
                <img alt="" className="absolute block inset-0 max-w-none size-full" src={imgEllipse} />
              </div>
              <p className="[word-break:break-word] font-['Manrope:Medium'] font-medium leading-[1.3] relative shrink-0 text-[12px] text-[color:var(--text\/secondary,rgba(255,255,255,0.88))] text-left whitespace-nowrap" data-node-id="6:472">
                2
              </p>
              <IconChevronRight className="relative shrink-0 size-[18px]" />
            </div>
          </a>
          <div className="border-[var(--glass\/divider,rgba(255,255,255,0.12))] border-solid border-t content-stretch flex gap-[12px] items-center min-h-[52px] overflow-clip pl-[16px] pr-[14px] py-[8px] relative shrink-0 w-full" data-node-id="6:489" data-name="Row · Strength">
            <div className="bg-[var(--glass\/fill,rgba(255,255,255,0.11))] content-stretch flex items-center justify-center overflow-clip relative rounded-[10px] shrink-0 size-[32px]" data-node-id="6:485" data-name="Row icon">
              <IconStrength className="relative shrink-0 size-[18px]" />
            </div>
            <div className="content-stretch flex flex-[1_0_0] flex-col items-start min-w-px overflow-clip relative" data-node-id="6:490" data-name="Frame">
              <div className="content-stretch flex items-center overflow-clip relative shrink-0 w-full" data-node-id="6:491" data-name="Frame">
                <p className="[word-break:break-word] flex-[1_0_0] font-['Manrope:SemiBold'] font-semibold leading-[1.3] min-w-px relative text-[15px] text-[color:var(--text\/primary,white)]" data-node-id="6:492">
                  Strength
                </p>
              </div>
            </div>
            <div className="content-stretch flex items-center overflow-clip relative shrink-0" data-node-id="6:482" data-name="Frame">
              <IconChevronRight className="relative shrink-0 size-[18px]" />
            </div>
          </div>
          <div className="border-[var(--glass\/divider,rgba(255,255,255,0.12))] border-solid border-t content-stretch flex gap-[12px] items-center min-h-[52px] overflow-clip pl-[16px] pr-[14px] py-[8px] relative shrink-0 w-full" data-node-id="6:501" data-name="Row · Speed">
            <div className="bg-[var(--glass\/fill,rgba(255,255,255,0.11))] content-stretch flex items-center justify-center overflow-clip relative rounded-[10px] shrink-0 size-[32px]" data-node-id="6:498" data-name="Row icon">
              <IconSpeed className="relative shrink-0 size-[18px]" />
            </div>
            <div className="content-stretch flex flex-[1_0_0] flex-col items-start min-w-px overflow-clip relative" data-node-id="6:502" data-name="Frame">
              <div className="content-stretch flex items-center overflow-clip relative shrink-0 w-full" data-node-id="6:503" data-name="Frame">
                <p className="[word-break:break-word] flex-[1_0_0] font-['Manrope:SemiBold'] font-semibold leading-[1.3] min-w-px relative text-[15px] text-[color:var(--text\/primary,white)]" data-node-id="6:504">
                  Speed
                </p>
              </div>
            </div>
            <div className="content-stretch flex gap-[6px] items-center overflow-clip relative shrink-0" data-node-id="6:493" data-name="Frame">
              <div className="relative shrink-0 size-[8px]" data-node-id="6:494" data-name="Ellipse">
                <img alt="" className="absolute block inset-0 max-w-none size-full" src={imgEllipse} />
              </div>
              <p className="[word-break:break-word] font-['Manrope:Medium'] font-medium leading-[1.3] relative shrink-0 text-[12px] text-[color:var(--text\/secondary,rgba(255,255,255,0.88))] whitespace-nowrap" data-node-id="6:495">
                1
              </p>
              <IconChevronRight className="relative shrink-0 size-[18px]" />
            </div>
          </div>
          <div className="border-[var(--glass\/divider,rgba(255,255,255,0.12))] border-solid border-t content-stretch flex gap-[12px] items-center min-h-[52px] overflow-clip pl-[16px] pr-[14px] py-[8px] relative shrink-0 w-full" data-node-id="6:511" data-name="Row · Support">
            <div className="bg-[var(--glass\/fill,rgba(255,255,255,0.11))] content-stretch flex items-center justify-center overflow-clip relative rounded-[10px] shrink-0 size-[32px]" data-node-id="6:508" data-name="Row icon">
              <IconSupportPaint className="relative shrink-0 size-[18px]" />
            </div>
            <div className="content-stretch flex flex-[1_0_0] flex-col items-start min-w-px overflow-clip relative" data-node-id="6:512" data-name="Frame">
              <div className="content-stretch flex items-center overflow-clip relative shrink-0 w-full" data-node-id="6:513" data-name="Frame">
                <p className="[word-break:break-word] flex-[1_0_0] font-['Manrope:SemiBold'] font-semibold leading-[1.3] min-w-px relative text-[15px] text-[color:var(--text\/primary,white)]" data-node-id="6:514">
                  Support
                </p>
              </div>
            </div>
            <div className="content-stretch flex items-center overflow-clip relative shrink-0" data-node-id="6:505" data-name="Frame">
              <IconChevronRight className="relative shrink-0 size-[18px]" />
            </div>
          </div>
          <div className="border-[var(--glass\/divider,rgba(255,255,255,0.12))] border-solid border-t content-stretch flex gap-[12px] items-center min-h-[52px] overflow-clip pl-[16px] pr-[14px] py-[8px] relative shrink-0 w-full" data-node-id="6:521" data-name="Row · Multimaterial">
            <div className="bg-[var(--glass\/fill,rgba(255,255,255,0.11))] content-stretch flex items-center justify-center overflow-clip relative rounded-[10px] shrink-0 size-[32px]" data-node-id="6:518" data-name="Row icon">
              <IconMultimaterial className="relative shrink-0 size-[18px]" />
            </div>
            <div className="content-stretch flex flex-[1_0_0] flex-col items-start min-w-px overflow-clip relative" data-node-id="6:522" data-name="Frame">
              <div className="content-stretch flex items-center overflow-clip relative shrink-0 w-full" data-node-id="6:523" data-name="Frame">
                <p className="[word-break:break-word] flex-[1_0_0] font-['Manrope:SemiBold'] font-semibold leading-[1.3] min-w-px relative text-[15px] text-[color:var(--text\/primary,white)]" data-node-id="6:524">
                  Multimaterial
                </p>
              </div>
            </div>
            <div className="content-stretch flex items-center overflow-clip relative shrink-0" data-node-id="6:515" data-name="Frame">
              <IconChevronRight className="relative shrink-0 size-[18px]" />
            </div>
          </div>
          <div className="border-[var(--glass\/divider,rgba(255,255,255,0.12))] border-solid border-t content-stretch flex gap-[12px] items-center min-h-[52px] overflow-clip pl-[16px] pr-[14px] py-[8px] relative shrink-0 w-full" data-node-id="6:533" data-name="Row · Others">
            <div className="bg-[var(--glass\/fill,rgba(255,255,255,0.11))] content-stretch flex items-center justify-center overflow-clip relative rounded-[10px] shrink-0 size-[32px]" data-node-id="6:528" data-name="Row icon">
              <IconOthers className="relative shrink-0 size-[18px]" />
            </div>
            <div className="content-stretch flex flex-[1_0_0] flex-col items-start min-w-px overflow-clip relative" data-node-id="6:534" data-name="Frame">
              <div className="content-stretch flex items-center overflow-clip relative shrink-0 w-full" data-node-id="6:535" data-name="Frame">
                <p className="[word-break:break-word] flex-[1_0_0] font-['Manrope:SemiBold'] font-semibold leading-[1.3] min-w-px relative text-[15px] text-[color:var(--text\/primary,white)]" data-node-id="6:536">
                  Others
                </p>
              </div>
            </div>
            <div className="content-stretch flex items-center overflow-clip relative shrink-0" data-node-id="6:525" data-name="Frame">
              <IconChevronRight className="relative shrink-0 size-[18px]" />
            </div>
          </div>
          <div className="border-[var(--glass\/divider,rgba(255,255,255,0.12))] border-solid border-t content-stretch flex gap-[12px] items-center min-h-[52px] overflow-clip pl-[16px] pr-[14px] py-[8px] relative shrink-0 w-full" data-node-id="6:543" data-name="Row · Notes">
            <div className="bg-[var(--glass\/fill,rgba(255,255,255,0.11))] content-stretch flex items-center justify-center overflow-clip relative rounded-[10px] shrink-0 size-[32px]" data-node-id="6:540" data-name="Row icon">
              <IconNotes className="relative shrink-0 size-[18px]" />
            </div>
            <div className="content-stretch flex flex-[1_0_0] flex-col items-start min-w-px overflow-clip relative" data-node-id="6:544" data-name="Frame">
              <div className="content-stretch flex items-center overflow-clip relative shrink-0 w-full" data-node-id="6:545" data-name="Frame">
                <p className="[word-break:break-word] flex-[1_0_0] font-['Manrope:SemiBold'] font-semibold leading-[1.3] min-w-px relative text-[15px] text-[color:var(--text\/primary,white)]" data-node-id="6:546">
                  Notes
                </p>
              </div>
            </div>
            <div className="content-stretch flex items-center overflow-clip relative shrink-0" data-node-id="6:537" data-name="Frame">
              <IconChevronRight className="relative shrink-0 size-[18px]" />
            </div>
          </div>
        </div>
        <div className="h-[10px] relative shrink-0 w-px" data-node-id="6:444" data-name="Frame" />
        <div className="backdrop-blur-[10px] bg-[var(--glass\/fill,rgba(255,255,255,0.11))] border border-[var(--glass\/stroke,rgba(255,255,255,0.2))] border-solid content-stretch flex flex-col items-start overflow-clip relative rounded-[20px] shrink-0 w-full" data-node-id="6:566" data-name="List">
          <div className="content-stretch flex gap-[12px] items-center min-h-[52px] overflow-clip pl-[16px] pr-[14px] py-[8px] relative shrink-0 w-full" data-node-id="6:552" data-name="Row · Calibration">
            <div className="bg-[var(--glass\/fill,rgba(255,255,255,0.11))] content-stretch flex items-center justify-center overflow-clip relative rounded-[10px] shrink-0 size-[32px]" data-node-id="6:549" data-name="Row icon">
              <IconFlask className="relative shrink-0 size-[18px]" />
            </div>
            <div className="content-stretch flex flex-[1_0_0] flex-col gap-[2px] items-start min-w-px overflow-clip relative" data-node-id="6:553" data-name="Frame">
              <div className="content-stretch flex items-center overflow-clip relative shrink-0 w-full" data-node-id="6:554" data-name="Frame">
                <p className="[word-break:break-word] flex-[1_0_0] font-['Manrope:SemiBold'] font-semibold leading-[1.3] min-w-px relative text-[14px] text-[color:var(--text\/primary,white)]" data-node-id="6:555">
                  Calibration
                </p>
              </div>
              <p className="[word-break:break-word] font-['Manrope:Medium'] font-medium leading-[1.3] relative shrink-0 text-[12px] text-[color:var(--text\/secondary,rgba(255,255,255,0.88))] w-full" data-node-id="6:556">
                Temperature, flow, pressure advance, retraction, VFA
              </p>
            </div>
            <IconChevronRight className="relative shrink-0 size-[18px]" />
          </div>
          <div className="border-[var(--glass\/divider,rgba(255,255,255,0.12))] border-solid border-t content-stretch flex gap-[12px] items-center min-h-[52px] overflow-clip pl-[16px] pr-[14px] py-[8px] relative shrink-0 w-full" data-node-id="6:562" data-name="Row · Import / export presets">
            <div className="bg-[var(--glass\/fill,rgba(255,255,255,0.11))] content-stretch flex items-center justify-center overflow-clip relative rounded-[10px] shrink-0 size-[32px]" data-node-id="6:559" data-name="Row icon">
              <IconUpload className="relative shrink-0 size-[18px]" />
            </div>
            <div className="content-stretch flex flex-[1_0_0] flex-col items-start min-w-px overflow-clip relative" data-node-id="6:563" data-name="Frame">
              <div className="content-stretch flex items-center overflow-clip relative shrink-0 w-full" data-node-id="6:564" data-name="Frame">
                <p className="[word-break:break-word] flex-[1_0_0] font-['Manrope:SemiBold'] font-semibold leading-[1.3] min-w-px relative text-[14px] text-[color:var(--text\/primary,white)]" data-node-id="6:565">
                  Import / export presets
                </p>
              </div>
            </div>
            <IconChevronRight className="relative shrink-0 size-[18px]" />
          </div>
        </div>
      </div>
      <NavBar className="absolute backdrop-blur-[10px] bg-[var(--glass\/fill,rgba(255,255,255,0.11))] border border-[var(--glass\/stroke,rgba(255,255,255,0.2))] border-solid content-stretch flex gap-[4px] h-[68px] items-start left-[16px] p-[6px] rounded-[34px] top-[752px] w-[358px]" />
    </div>
  );
}
