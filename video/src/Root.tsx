import { Composition } from "remotion";
import { Sprout, TOTAL } from "./Sprout";

export const Root: React.FC = () => (
  <Composition
    id="Sprout"
    component={Sprout}
    durationInFrames={TOTAL}
    fps={30}
    width={1920}
    height={1080}
  />
);
